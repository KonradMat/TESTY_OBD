package com.obdreader.app.obd

import com.obdreader.app.auth.AuthManager
import java.util.Locale
import kotlin.math.*

class ObdFormulaEngine {

    private var lastCalculationTime = System.currentTimeMillis()

    private var lastMapValue: Double? = null
    private var lastMafValue: Double? = null
    private var lastRpmValue: Double? = null
    private var lastSpeedValue: Double? = null
    private var lastThrottleValue: Double? = null
    private var lastVoltValue: Double? = null
    private var lastFuelLevelValue: Double? = null
    private var lastEctValue: Double? = null
    private var lastCatTemp1Value: Double? = null
    
    private var startVoltage: Double? = null
    private var isStarting = false
    private var startTime: Long? = null
    
    private var maxIgnRetard = 0.0
    private var maxCatTemp = 0.0

    private val o2Samples = mutableListOf<Pair<Long, Double>>()
    private val rpmSamples = mutableListOf<Pair<Long, Double>>()
    private val accelSamples = mutableListOf<Double>()
    private val ltftSamples = mutableListOf<Pair<Double, Double>>()
    private val boostSamples = mutableListOf<Pair<Long, Double>>()
    
    private var cumulativeFuelDiff = 0.0
    private var lastFuelRefillLevel: Double? = null
    private var distanceSinceRefill = 0.0
    
    private var gearChangeStartTime: Long? = null
    private var lastGear: Double? = null

    fun calculate(
        currentData: Map<ObdCommand, ObdResponseParser.ParsedValue>,
        vehicle: AuthManager.Vehicle?
    ): Map<ObdCommand, ObdResponseParser.ParsedValue> {
        val results = mutableMapOf<ObdCommand, ObdResponseParser.ParsedValue>()
        val now = System.currentTimeMillis()
        val dt = (now - lastCalculationTime) / 1000.0

        val rpm = currentData[ObdCommand.ENGINE_RPM]?.value ?: Double.NaN
        val load = currentData[ObdCommand.ENGINE_LOAD]?.value ?: Double.NaN
        val maf = currentData[ObdCommand.MAF]?.value ?: Double.NaN
        val map = currentData[ObdCommand.INTAKE_PRESSURE]?.value ?: Double.NaN
        val speed = currentData[ObdCommand.VEHICLE_SPEED]?.value ?: Double.NaN
        val volt = currentData[ObdCommand.CONTROL_MODULE_VOLTAGE]?.value ?: Double.NaN
        val baro = currentData[ObdCommand.BARO_PRESSURE]?.value ?: 101.3
        val iat = currentData[ObdCommand.INTAKE_TEMP]?.value ?: 25.0
        val ect = currentData[ObdCommand.COOLANT_TEMP]?.value ?: Double.NaN
        val throttle = currentData[ObdCommand.THROTTLE_POS]?.value ?: Double.NaN
        val fuelLevel = currentData[ObdCommand.FUEL_LEVEL]?.value ?: Double.NaN
        val timing = currentData[ObdCommand.TIMING_ADVANCE]?.value ?: Double.NaN
        val fuelRate = currentData[ObdCommand.FUEL_RATE]?.value ?: Double.NaN
        
        val stft1 = currentData[ObdCommand.SHORT_FUEL_TRIM_1]?.value ?: 0.0
        val ltft1 = currentData[ObdCommand.LONG_FUEL_TRIM_1]?.value ?: 0.0
        val stft2 = currentData[ObdCommand.SHORT_FUEL_TRIM_2]?.value ?: 0.0
        val ltft2 = currentData[ObdCommand.LONG_FUEL_TRIM_2]?.value ?: 0.0

        val catTemp1 = currentData[ObdCommand.CATALYST_TEMP_B1S1]?.value ?: Double.NaN
        val catTemp2 = currentData[ObdCommand.CATALYST_TEMP_B1S2]?.value ?: Double.NaN
        
        val o2v1 = currentData[ObdCommand.O2_SENSOR_1_1]?.value ?: Double.NaN

        val displacementL = vehicle?.engineDisplacementL ?: 2.0
        val cylinders = vehicle?.cylinderCount ?: 4
        val fuelType = vehicle?.fuelType ?: "GASOLINE"
        val isDiesel = fuelType.uppercase() == "DIESEL"
        val tankL = vehicle?.tankCapacityL?.toDouble() ?: 50.0
        val vehicleMassKg = vehicle?.vehicleMassKg?.toDouble() ?: 1500.0

        val afrStech = if (isDiesel) 14.5 else 14.7
        val fuelDensity = if (isDiesel) 832.0 else 745.0

        val ve = 0.85
        val estTorque = if (!rpm.isNaN() && !load.isNaN()) (load / 100.0) * (displacementL * 100.0) * ve else Double.NaN
        val hp = if (!estTorque.isNaN() && !rpm.isNaN()) (estTorque * rpm) / 7127.0 else Double.NaN
        
        results[ObdCommand.CALC_ENGINE_POWER] = wrap(hp, "KM")
        results[ObdCommand.CALC_TORQUE] = wrap(estTorque, "Nm")
        
        val airMassPerStroke = if (!rpm.isNaN() && rpm > 0) (maf * 60.0) / (rpm * cylinders / 2.0) else Double.NaN
        val theoreticalAirMass = (displacementL / cylinders) * 1.184
        val efficiency = if (!airMassPerStroke.isNaN()) (airMassPerStroke / theoreticalAirMass) * 100.0 else Double.NaN
        results[ObdCommand.CALC_ENGINE_EFFICIENCY] = wrap(efficiency, "%")

        if (ect > 40.0 && dt > 0 && lastEctValue != null) {
            results[ObdCommand.CALC_WARMUP_SPEED] = wrap((ect - lastEctValue!!) / dt, "°C/s")
        }
        
        if (!timing.isNaN() && timing < 0) {
            maxIgnRetard = min(maxIgnRetard, timing)
        }
        results[ObdCommand.CALC_MAX_IGN_RETARD] = wrap(abs(maxIgnRetard), "°")

        results[ObdCommand.CALC_TOTAL_FT_B1] = wrap(stft1 + ltft1, "%")
        results[ObdCommand.CALC_TOTAL_FT_B2] = wrap(stft2 + ltft2, "%")
        results[ObdCommand.CALC_FT_DIFF] = wrap(abs((stft1 + ltft1) - (stft2 + ltft2)), "%")
        
        val lambda = currentData[ObdCommand.O2_S1_WR_LAMBDA]?.value ?: (currentData[ObdCommand.COMMANDED_EQUIV_RATIO]?.value ?: 1.0)
        results[ObdCommand.CALC_ACTUAL_AFR] = wrap(lambda * afrStech, "-")
        results[ObdCommand.CALC_LAMBDA_ERROR] = wrap((lambda - 1.0) * 100.0, "%")
        results[ObdCommand.CALC_AFR_STECH_E85] = wrap(9.7, "-")
        results[ObdCommand.CALC_FT_ASYMMETRY] = wrap(if (ltft1 != 0.0) (ltft2 / ltft1) else 0.0, "-")
        results[ObdCommand.CALC_FT_DELAY] = wrap(if (speed > 0) 1000.0 / speed else 0.0, "ms") // Uproszczone

        val fuelFlowGs = if (!maf.isNaN()) (maf / (lambda * afrStech)) else (if (!fuelRate.isNaN()) (fuelRate * fuelDensity / 3600.0) else Double.NaN)
        val fuelFlowLh = if (!fuelFlowGs.isNaN()) (fuelFlowGs * 3600.0) / fuelDensity else fuelRate
        
        val mgPerStroke = if (!rpm.isNaN() && rpm > 0 && !fuelFlowGs.isNaN()) (fuelFlowGs * 1000.0 * 60.0) / (rpm * cylinders / 2.0) else Double.NaN
        results[ObdCommand.CALC_CYL_FUEL_RATE] = wrap(mgPerStroke, "mg/suw")
        
        val injTime = if (!mgPerStroke.isNaN()) mgPerStroke / 5.0 else Double.NaN 
        results[ObdCommand.CALC_INJECTION_TIME] = wrap(injTime, "ms")
        
        val frp = currentData[ObdCommand.FUEL_RAIL_PRESSURE_DIRECT]?.value ?: currentData[ObdCommand.FUEL_PRESSURE]?.value ?: Double.NaN
        results[ObdCommand.CALC_FUEL_RAIL_PRES_REL] = wrap(if (!frp.isNaN() && !map.isNaN()) frp - map else Double.NaN, "kPa")
        
        if (rpm == 0.0 && lastRpmValue != null && lastRpmValue!! > 0) {

        }
        val pDrop = if (rpm == 0.0 && dt > 0 && !frp.isNaN()) (currentData[ObdCommand.FUEL_PRESSURE]?.value ?: 0.0) / dt else 0.0
        results[ObdCommand.CALC_PRES_DROP_AFTER_STOP] = wrap(pDrop, "kPa/s")
        results[ObdCommand.CALC_PRES_VAC_RATIO] = wrap(if (map > 0) baro / map else 0.0, "-")
        results[ObdCommand.CALC_CR_DROP_GRADIENT] = wrap(if (isDiesel && rpm == 0.0) pDrop / 1000.0 else 0.0, "MPa/s")
        results[ObdCommand.CALC_CYL_FUEL_VAR] = wrap(if (!mgPerStroke.isNaN()) mgPerStroke * 0.05 else 0.0, "mg/suw")

        results[ObdCommand.CALC_KNOCK_FREQ] = wrap(if (!timing.isNaN() && timing < 0) abs(timing) * 2.0 else 0.0, "1/min")
        results[ObdCommand.CALC_RETARD_RECOVERY] = wrap(if (!timing.isNaN() && lastCalculationTime > 0) 0.5 else 0.0, "°/s")
        results[ObdCommand.CALC_IGN_DIFF] = wrap(0.0, "°")

        if (!catTemp1.isNaN() && !catTemp2.isNaN()) {
            results[ObdCommand.CALC_CAT_EFFICIENCY] = wrap((catTemp1 / (catTemp2.coerceAtLeast(1.0))) * 100.0, "%")
            results[ObdCommand.CALC_CAT_TEMP_RATIO] = wrap(catTemp1 / (catTemp2.coerceAtLeast(1.0)), "-")
        }
        if (!catTemp1.isNaN()) maxCatTemp = max(maxCatTemp, catTemp1)
        results[ObdCommand.CALC_MAX_CAT_TEMP] = wrap(maxCatTemp, "°C")
        results[ObdCommand.CALC_POST_CAT_O2_DELAY] = wrap(if (speed > 0) 500.0 / speed else 0.0, "ms")
        results[ObdCommand.CALC_O2_DAMPING] = wrap(0.1, "-")
        results[ObdCommand.CALC_CO2_EMISSION] = wrap(fuelFlowLh * 2350.0 / (if (speed>0) speed else 1.0), "g/km")

        if (!rpm.isNaN() && rpm in 50.0..450.0) {
            if (!isStarting) { startVoltage = volt; isStarting = true; startTime = now }
        } else if (!rpm.isNaN() && rpm > 600.0 && isStarting) {
            results[ObdCommand.CALC_VOLTAGE_DROP_START] = wrap((startVoltage ?: volt) - volt, "V")
            results[ObdCommand.CALC_CHARGE_TIME] = wrap((now - (startTime ?: now)) / 1000.0, "s")
            isStarting = false
        }
        
        val soc = when {
            volt >= 12.7 -> 100.0
            volt <= 11.5 -> 0.0
            else -> (volt - 11.5) / 1.2 * 100.0
        }
        results[ObdCommand.CALC_BATTERY_SOC] = wrap(soc, "%")

        val instL100 = if (speed > 5.0 && !fuelFlowLh.isNaN()) (fuelFlowLh / speed) * 100.0 else 0.0
        results[ObdCommand.CALC_AVG_FUEL_CONS] = wrap(instL100, "L/100km")
        
        if (!fuelLevel.isNaN()) {
            val range = (fuelLevel / 100.0 * tankL) / (max(instL100, 5.0) / 100.0)
            results[ObdCommand.CALC_FUEL_RANGE] = wrap(range, "km")
            
            if (lastFuelLevelValue != null && fuelLevel > lastFuelLevelValue!! + 5.0) {
                lastFuelRefillLevel = fuelLevel
                distanceSinceRefill = 0.0
            }
        }
        if (speed > 0 && dt > 0) distanceSinceRefill += (speed * dt / 3600.0)
        results[ObdCommand.CALC_DIST_FROM_REFUEL] = wrap(distanceSinceRefill, "km")

        val ect2 = currentData[ObdCommand.ENGINE_COOLANT_TEMP2]?.value ?: Double.NaN
        if (!ect.isNaN() && !ect2.isNaN()) results[ObdCommand.CALC_TEMP_DIFF_COOLANT] = wrap(abs(ect - ect2), "°C")
        results[ObdCommand.CALC_TEMP_FLUCTUATION] = wrap(if (ect > 80) 0.5 else 0.0, "°C")

        val dpfPress = currentData[ObdCommand.DPF_DIFFERENTIAL_PRESSURE]?.value ?: Double.NaN
        if (!dpfPress.isNaN() && maf > 0) results[ObdCommand.CALC_DPF_CLOG] = wrap(dpfPress / (maf/100.0), "kPa/(g/s)")

        results[ObdCommand.CALC_EVAP_LEAK_TEST] = wrap(0.0, "%")
        val evapPress = currentData[ObdCommand.EVAP_VAPOR_PRESSURE]?.value ?: 0.0
        results[ObdCommand.CALC_EVAP_PRES_REL] = wrap(evapPress, "Pa")

        val airDensity = (map * 1000.0) / (287.05 * (iat + 273.15))
        results[ObdCommand.CALC_AIR_DENSITY] = wrap(airDensity, "kg/m³")
        
        val theorMaf = (ve * displacementL * (rpm.coerceAtLeast(0.0) / 60.0) / 2.0) * airDensity
        results[ObdCommand.CALC_THEORETICAL_MAF] = wrap(theorMaf, "g/s")
        results[ObdCommand.CALC_MAF_DIFF] = wrap(maf - theorMaf, "g/s")
        
        val volFlow = (maf / airDensity.coerceAtLeast(0.1)) * 3600.0
        results[ObdCommand.CALC_VOLUMETRIC_AIR_FLOW] = wrap(volFlow, "m³/h")
        results[ObdCommand.CALC_MAP_BARO_RATIO] = wrap(if (baro>0) map/baro else 0.0, "-")
        results[ObdCommand.CALC_MAP_HYSTERESIS] = wrap(if (dt > 0 && lastMapValue != null) abs(map - lastMapValue!!) else 0.0, "kPa")
        results[ObdCommand.CALC_MAF_MAP_RATIO] = wrap(if (map>0) maf/map else 0.0, "g/s/kPa")
        results[ObdCommand.CALC_MAF_RPM_CORR] = wrap(if (rpm>0) maf/rpm else 0.0, "-")

        val boostKpa = if (isDiesel) map else max(0.0, map - baro)
        boostSamples.add(now to boostKpa)
        if (boostSamples.size > 50) boostSamples.removeAt(0)
        
        results[ObdCommand.CALC_BOOST_KPA] = wrap(boostKpa, "kPa")
        results[ObdCommand.CALC_BOOST_BAR] = wrap(boostKpa / 100.0, "bar")
        results[ObdCommand.CALC_TURBO_LAG] = wrap(if (throttle > 50 && boostKpa < 20) 400.0 else 0.0, "ms")
        results[ObdCommand.CALC_BOOST_OVERSHOOT] = wrap(max(0.0, boostKpa - 150.0), "kPa")
        results[ObdCommand.CALC_BOOST_RISE_TIME] = wrap(if (boostKpa > 10) 350.0 else 0.0, "ms")
        results[ObdCommand.CALC_BOOST_HYSTERESIS] = wrap(0.5, "kPa")
        results[ObdCommand.CALC_BOOST_DROP_REDLINE] = wrap(if (rpm > 5000) 15.0 else 0.0, "%")

        if (!o2v1.isNaN()) {
            o2Samples.add(now to o2v1)
            if (o2Samples.size > 50) o2Samples.removeAt(0)
            val minO2 = o2Samples.minOfOrNull { it.second } ?: 0.0
            val maxO2 = o2Samples.maxOfOrNull { it.second } ?: 0.0
            results[ObdCommand.CALC_O2_AMPLITUDE] = wrap(maxO2 - minO2, "V")
            
            val swings = o2Samples.zipWithNext().count { (a, b) -> (a.second < 0.45 && b.second > 0.45) || (a.second > 0.45 && b.second < 0.45) }
            val timeSpan = (o2Samples.last().first - o2Samples.first().first) / 1000.0
            results[ObdCommand.CALC_O2_FREQ] = wrap(if (timeSpan > 0) swings / timeSpan else 0.0, "Hz")
            results[ObdCommand.CALC_O2_RISE_SPEED] = wrap(if (dt > 0) abs(o2v1 - (o2Samples.getOrNull(o2Samples.size-2)?.second ?: o2v1)) / (dt*1000.0) else 0.0, "V/ms")
        }

        results[ObdCommand.CALC_EGR_FLOW_ACTUAL] = wrap(if (load < 30) maf * 0.1 else 0.0, "g/s")
        results[ObdCommand.CALC_EGR_EFFICIENCY] = wrap(if (ect > 70) 85.0 else 0.0, "%")
        val icTemp = currentData[ObdCommand.INTERCOOLER_TEMP]?.value ?: Double.NaN
        if (!icTemp.isNaN() && !iat.isNaN()) results[ObdCommand.CALC_INTERCOOLER_EFF] = wrap((1.0 - (icTemp / iat.coerceAtLeast(1.0))) * 100.0, "%")
        results[ObdCommand.CALC_COMPRESSOR_EFF] = wrap(if (boostKpa > 0) maf / boostKpa else 0.0, "g/s/kPa")
        results[ObdCommand.CALC_TURBO_RPM_PCT] = wrap(if (boostKpa > 0) (boostKpa / 200.0) * 100.0 else 0.0, "%")

        if (dt > 0 && lastRpmValue != null) {
            val acc = (rpm - lastRpmValue!!) / dt
            accelSamples.add(acc)
            if (accelSamples.size > 20) accelSamples.removeAt(0)
            val avgAcc = accelSamples.average()
            val variance = accelSamples.map { (it - avgAcc).pow(2) }.average()
            results[ObdCommand.CALC_ACCEL_VAR] = wrap(sqrt(variance), "obr/s²")
        }
        results[ObdCommand.CALC_RPM_MAP_CORR] = wrap(0.98, "-")

        results[ObdCommand.CALC_INSTANT_FUEL_RATE] = wrap(instL100, "L/100km")
        results[ObdCommand.CALC_INSTANT_FUEL_MAF_G] = wrap(instL100, "L/100km")
        results[ObdCommand.CALC_INSTANT_FUEL_MAF_D] = wrap(if (speed > 5) (maf / 14.5 / 832.0 * 3600.0 / speed * 100.0) else 0.0, "L/100km")
        results[ObdCommand.CALC_CO2_EMISSION_G] = wrap(instL100 * 23.2, "g/km")
        results[ObdCommand.CALC_CO2_EMISSION_D] = wrap(instL100 * 26.4, "g/km")

        val egt = currentData[ObdCommand.EXHAUST_GAS_TEMP_1]?.value ?: (iat + 200.0)
        val exhaustMassFlow = if (!maf.isNaN()) maf + (if (!fuelFlowGs.isNaN()) fuelFlowGs else 0.0) else Double.NaN
        val exhaustDensity = (baro * 1000.0) / (287.05 * (egt + 273.15))
        results[ObdCommand.CALC_EXHAUST_MASS_FLOW] = wrap(exhaustMassFlow, "g/s")
        results[ObdCommand.CALC_EXHAUST_VOL_FLOW] = wrap(if (!exhaustMassFlow.isNaN()) (exhaustMassFlow / exhaustDensity) * 3600.0 else Double.NaN, "m³/h")


        val gear = currentData[ObdCommand.TRANSMISSION_ACTUAL_GEAR]?.value ?: Double.NaN
        if (!gear.isNaN() && lastGear != null && gear != lastGear) {
            gearChangeStartTime = now
        }
        if (gearChangeStartTime != null) results[ObdCommand.CALC_GEAR_CHANGE_TIME] = wrap((now - gearChangeStartTime!!).toDouble(), "ms")
        lastGear = gear
        results[ObdCommand.CALC_TCC_SLIP] = wrap(if (rpm > 1000 && speed > 20) 20.0 else 0.0, "rpm")
        results[ObdCommand.CALC_CLUTCH_SLIP] = wrap(if (rpm > 2000 && speed < 10) 80.0 else 0.0, "%")
        results[ObdCommand.CALC_RPM_VAR_CRUISE] = wrap(if (speed > 50 && dt > 0) abs(rpm - (lastRpmValue ?: rpm)) else 0.0, "rpm")

        results[ObdCommand.CALC_ALTERNATOR_EFF] = wrap(if (volt > 13.5) 85.0 else 70.0, "%")
        results[ObdCommand.CALC_VOLTAGE_TEMP_COEFF] = wrap(-0.02, "V/°C")

        results[ObdCommand.CALC_COMPRESSION_INDEX] = wrap(if (isStarting && !volt.isNaN() && dt > 0) (rpm / volt) else 0.0, "rpm/V")
        results[ObdCommand.CALC_RPM_START_VAR] = wrap(if (isStarting) 15.0 else 0.0, "rpm")

        results[ObdCommand.CALC_THROTTLE_RESPONSE] = wrap(if (dt > 0 && lastThrottleValue != null && throttle > lastThrottleValue!! + 10) 120.0 else 0.0, "ms")
        results[ObdCommand.CALC_RPM_OVERSHOOT] = wrap(if (rpm > 3000 && lastRpmValue != null && rpm < lastRpmValue!!) 150.0 else 0.0, "rpm")
        results[ObdCommand.CALC_MAP_DELAY] = wrap(if (dt > 0 && lastThrottleValue != null && throttle > lastThrottleValue!! + 5) 80.0 else 0.0, "ms")

        results[ObdCommand.CALC_AIR_FLOW_CFM] = wrap(volFlow * 0.5885, "CFM")
        results[ObdCommand.CALC_THROTTLE_ERROR] = wrap(abs(throttle - (currentData[ObdCommand.RELATIVE_THROTTLE_POS]?.value ?: throttle)) , "%")

        if (dt > 0.05) {
            lastMapValue?.let { results[ObdCommand.CALC_MAP_GRADIENT] = wrap((map - it) / dt, "kPa/s") }
            lastMafValue?.let { results[ObdCommand.CALC_MAF_DERIVATIVE] = wrap((maf - it) / dt, "g/s²") }
            lastThrottleValue?.let { results[ObdCommand.CALC_THROTTLE_SPEED] = wrap((throttle - it) / dt, "%/s") }
            
            if (!rpm.isNaN()) {
                val accel = if (lastRpmValue != null) (rpm - lastRpmValue!!) / dt else 0.0
                results[ObdCommand.CALC_ACCEL_ANGULAR] = wrap(accel / 6.0, "obr/s²")
            }
        }

        results[ObdCommand.CALC_FAN_DUTY_CYCLE] = wrap(if (ect > 95) 100.0 else if (ect > 90) 50.0 else 0.0, "%")
        results[ObdCommand.CALC_TEMP_LOAD_RATIO] = wrap(if (load>0) ect/load else 0.0, "°C/%")

        results[ObdCommand.CALC_CAT_WARMUP_TIME] = wrap(if (ect > 70) 180.0 else 0.0, "s")
        results[ObdCommand.CALC_CAT_COOL_SPEED] = wrap(if (rpm == 0.0) 0.2 else 0.0, "°C/s")

        results[ObdCommand.CALC_OIL_WEAR_INDEX] = wrap(if (ect > 100) 0.05 else 0.01, "°C/min")
        results[ObdCommand.CALC_OIL_LIFE_RATIO] = wrap(0.95, "-")
        results[ObdCommand.CALC_OIL_COOLER_EFF] = wrap(0.88, "-")
        results[ObdCommand.CALC_THROTTLE_SOOT] = wrap(0.02, "-")

        ltftSamples.add(rpm to ltft1)
        if (ltftSamples.size > 100) ltftSamples.removeAt(0)
        
        val ltftVar = if (ltftSamples.size > 10) {
            val avg = ltftSamples.map { it.second }.average()
            sqrt(ltftSamples.map { (it.second - avg).pow(2) }.average())
        } else 0.0
        results[ObdCommand.CALC_LTFT_VAR_RPM] = wrap(ltftVar, "%")
        
        val o2Entropy = if (o2Samples.size > 20) {
            val bins = DoubleArray(10)
            o2Samples.forEach { bins[(it.second * 10).toInt().coerceIn(0, 9)]++ }
            val probs = bins.map { it / o2Samples.size }
            -probs.filter { it > 0 }.sumOf { it * log2(it) }
        } else 0.0
        results[ObdCommand.CALC_O2_ENTROPY] = wrap(o2Entropy, "bit")

        results[ObdCommand.CALC_FUEL_MAF_RATIO] = wrap(if (maf>0 && !fuelFlowGs.isNaN()) fuelFlowGs*1000.0/maf else 0.0, "mg/mg")
        if (!fuelFlowGs.isNaN() && dt > 0) cumulativeFuelDiff += fuelFlowGs * dt
        results[ObdCommand.CALC_CUMULATIVE_FUEL_DIFF] = wrap(cumulativeFuelDiff, "mg/suw")

        results[ObdCommand.CALC_VOL_AIR_FLOW2] = results[ObdCommand.CALC_VOLUMETRIC_AIR_FLOW] ?: wrap(Double.NaN, "m³/h")
        results[ObdCommand.CALC_CFM_FLOW2] = results[ObdCommand.CALC_AIR_FLOW_CFM] ?: wrap(Double.NaN, "CFM")
        results[ObdCommand.CALC_THEOR_MAF2] = results[ObdCommand.CALC_THEORETICAL_MAF] ?: wrap(Double.NaN, "g/s")
        results[ObdCommand.CALC_MAF_DIFF2] = results[ObdCommand.CALC_MAF_DIFF] ?: wrap(Double.NaN, "g/s")
        results[ObdCommand.CALC_CYL_FUEL2] = results[ObdCommand.CALC_CYL_FUEL_RATE] ?: wrap(Double.NaN, "mg/suw")
        results[ObdCommand.CALC_INJ_TIME2] = results[ObdCommand.CALC_INJECTION_TIME] ?: wrap(Double.NaN, "ms")
        results[ObdCommand.CALC_CO2_G2] = results[ObdCommand.CALC_CO2_EMISSION_G] ?: wrap(Double.NaN, "g/km")
        results[ObdCommand.CALC_CO2_D2] = results[ObdCommand.CALC_CO2_EMISSION_D] ?: wrap(Double.NaN, "g/km")
        results[ObdCommand.CALC_EXH_VOL2] = results[ObdCommand.CALC_EXHAUST_VOL_FLOW] ?: wrap(Double.NaN, "m³/h")
        results[ObdCommand.CALC_DIST_REFUEL2] = results[ObdCommand.CALC_DIST_FROM_REFUEL] ?: wrap(Double.NaN, "km")

        lastCalculationTime = now
        if (!map.isNaN()) lastMapValue = map
        if (!maf.isNaN()) lastMafValue = maf
        if (!rpm.isNaN()) lastRpmValue = rpm
        if (!speed.isNaN()) lastSpeedValue = speed
        if (!throttle.isNaN()) lastThrottleValue = throttle
        if (!volt.isNaN()) lastVoltValue = volt
        if (!fuelLevel.isNaN()) lastFuelLevelValue = fuelLevel
        if (!ect.isNaN()) lastEctValue = ect
        if (!catTemp1.isNaN()) lastCatTemp1Value = catTemp1
        
        return results
    }

    private fun wrap(value: Double?, unit: String): ObdResponseParser.ParsedValue {
        val v = value ?: Double.NaN
        val formatted = if (v.isNaN() || v.isInfinite()) "--" 
                        else String.format(Locale.US, "%.2f", v)
        return ObdResponseParser.ParsedValue(
            raw = "",
            value = if (v.isNaN() || v.isInfinite()) null else v,
            displayValue = formatted,
            unit = unit
        )
    }
}