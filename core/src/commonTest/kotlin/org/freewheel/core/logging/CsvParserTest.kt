package org.freewheel.core.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CsvParserTest {

    // ==================== Basic Parsing ====================

    @Test
    fun `parse empty string returns empty list`() {
        assertEquals(emptyList(), CsvParser.parse(""))
    }

    @Test
    fun `parse header only returns empty list`() {
        val csv = "date,time,speed,voltage,phase_current,current,power,torque,pwm," +
            "battery_level,distance,totaldistance,system_temp,temp2,tilt,roll,mode,alert"
        assertEquals(emptyList(), CsvParser.parse(csv))
    }

    @Test
    fun `parse missing required columns returns empty list`() {
        val csv = "foo,bar,baz\n1,2,3"
        assertEquals(emptyList(), CsvParser.parse(csv))
    }

    @Test
    fun `parse single row without GPS`() {
        val header = "date,time,speed,voltage,phase_current,current,power,torque,pwm," +
            "battery_level,distance,totaldistance,system_temp,temp2,tilt,roll,mode,alert"
        val row = "2024-01-15,10:30:00.000,25.50,84.00,15.50,12.00,1500.00,10.00,45.67," +
            "85,1234,999999,35,28,2.50,-1.30,Sport,"
        val csv = "$header\n$row"

        val samples = CsvParser.parse(csv)
        assertEquals(1, samples.size)

        val s = samples[0]
        assertEquals(25.5, s.speedKmh, 0.01)
        assertEquals(84.0, s.voltageV, 0.01)
        assertEquals(12.0, s.currentA, 0.01)
        assertEquals(1500.0, s.powerW, 0.01)
        assertEquals(35.0, s.temperatureC, 0.01)
        assertEquals(85.0, s.batteryPercent, 0.01)
        assertEquals(45.67, s.pwmPercent, 0.01)
        assertEquals(0.0, s.gpsSpeedKmh, 0.01) // no GPS column
    }

    @Test
    fun `parse single row with GPS`() {
        val header = "date,time,latitude,longitude,gps_speed,gps_alt,gps_heading,gps_distance," +
            "speed,voltage,phase_current,current,power,torque,pwm," +
            "battery_level,distance,totaldistance,system_temp,temp2,tilt,roll,mode,alert"
        val row = "2024-01-15,10:30:00.000,37.7749,-122.4194,20.00,50.50,90.00,1234," +
            "25.50,84.00,15.50,12.00,1500.00,10.00,45.67," +
            "85,100,999999,35,28,2.50,-1.30,Sport,"
        val csv = "$header\n$row"

        val samples = CsvParser.parse(csv)
        assertEquals(1, samples.size)

        val s = samples[0]
        assertEquals(25.5, s.speedKmh, 0.01)
        assertEquals(20.0, s.gpsSpeedKmh, 0.01)
    }

    @Test
    fun `parse multiple rows`() {
        val header = "date,time,speed,voltage,phase_current,current,power,torque,pwm," +
            "battery_level,distance,totaldistance,system_temp,temp2,tilt,roll,mode,alert"
        val row1 = "2024-01-15,10:30:00.000,20.00,84.00,0.00,0.00,0.00,0.00,0.00,80,0,0,30,0,0.00,0.00,,"
        val row2 = "2024-01-15,10:30:01.000,25.00,83.50,0.00,5.00,500.00,0.00,10.00,78,50,50,31,0,0.00,0.00,,"
        val row3 = "2024-01-15,10:30:02.000,30.00,83.00,0.00,10.00,1000.00,0.00,20.00,76,120,120,32,0,0.00,0.00,,"
        val csv = "$header\n$row1\n$row2\n$row3"

        val samples = CsvParser.parse(csv)
        assertEquals(3, samples.size)
        assertEquals(20.0, samples[0].speedKmh, 0.01)
        assertEquals(25.0, samples[1].speedKmh, 0.01)
        assertEquals(30.0, samples[2].speedKmh, 0.01)
    }

    // ==================== Timestamp Parsing ====================

    @Test
    fun `parse timestamp includes milliseconds`() {
        val header = "date,time,speed,voltage,phase_current,current,power,torque,pwm," +
            "battery_level,distance,totaldistance,system_temp,temp2,tilt,roll,mode,alert"
        val row1 = "2024-01-15,10:30:00.000,0.00,0.00,0.00,0.00,0.00,0.00,0.00,0,0,0,0,0,0.00,0.00,,"
        val row2 = "2024-01-15,10:30:01.000,0.00,0.00,0.00,0.00,0.00,0.00,0.00,0,0,0,0,0,0.00,0.00,,"
        val csv = "$header\n$row1\n$row2"

        val samples = CsvParser.parse(csv)
        assertEquals(2, samples.size)
        // Timestamps should differ by ~1000ms
        val diff = samples[1].timestampMs - samples[0].timestampMs
        assertEquals(1000, diff)
    }

    @Test
    fun `parse timestamp without milliseconds`() {
        val header = "date,time,speed,voltage,phase_current,current,power,torque,pwm," +
            "battery_level,distance,totaldistance,system_temp,temp2,tilt,roll,mode,alert"
        // No milliseconds in time
        val row = "2024-01-15,10:30:00,0.00,0.00,0.00,0.00,0.00,0.00,0.00,0,0,0,0,0,0.00,0.00,,"
        val csv = "$header\n$row"

        val samples = CsvParser.parse(csv)
        assertEquals(1, samples.size)
        assertTrue(samples[0].timestampMs > 0)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `parse skips blank lines`() {
        val header = "date,time,speed,voltage,phase_current,current,power,torque,pwm," +
            "battery_level,distance,totaldistance,system_temp,temp2,tilt,roll,mode,alert"
        val row = "2024-01-15,10:30:00.000,25.00,84.00,0.00,0.00,0.00,0.00,0.00,80,0,0,30,0,0.00,0.00,,"
        val csv = "$header\n\n$row\n\n"

        val samples = CsvParser.parse(csv)
        assertEquals(1, samples.size)
    }

    @Test
    fun `parse skips rows with too few columns`() {
        val header = "date,time,speed,voltage,phase_current,current,power,torque,pwm," +
            "battery_level,distance,totaldistance,system_temp,temp2,tilt,roll,mode,alert"
        val badRow = "2024-01-15,10:30:00.000"  // Only date and time
        val goodRow = "2024-01-15,10:30:01.000,25.00,84.00,0.00,0.00,0.00,0.00,0.00,80,0,0,30,0,0.00,0.00,,"
        val csv = "$header\n$badRow\n$goodRow"

        val samples = CsvParser.parse(csv)
        assertEquals(1, samples.size)
        assertEquals(25.0, samples[0].speedKmh, 0.01)
    }

    @Test
    fun `parse handles missing optional columns gracefully`() {
        // Minimal header with only required columns
        val header = "date,time,speed"
        val row = "2024-01-15,10:30:00.000,25.00"
        val csv = "$header\n$row"

        val samples = CsvParser.parse(csv)
        assertEquals(1, samples.size)
        assertEquals(25.0, samples[0].speedKmh, 0.01)
        assertEquals(0.0, samples[0].voltageV, 0.01)
        assertEquals(0.0, samples[0].currentA, 0.01)
    }

    // ==================== Downsampling ====================

    @Test
    fun `parse does not downsample short rides`() {
        val header = "date,time,speed"
        val rows = (0 until 100).joinToString("\n") { i ->
            "2024-01-15,10:${(i / 60).toString().padStart(2, '0')}:${(i % 60).toString().padStart(2, '0')}.000,${i.toDouble()}"
        }
        val csv = "$header\n$rows"

        val samples = CsvParser.parse(csv)
        assertEquals(100, samples.size)
    }

    @Test
    fun `parse downsamples rides over 3600 samples`() {
        val header = "date,time,speed"
        val totalSamples = 7200
        val rows = (0 until totalSamples).joinToString("\n") { i ->
            val hour = 10 + (i / 3600)
            val min = (i % 3600) / 60
            val sec = i % 60
            "2024-01-15,${hour.toString().padStart(2, '0')}:${min.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}.000,${i.toDouble()}"
        }
        val csv = "$header\n$rows"

        val samples = CsvParser.parse(csv)
        assertTrue(samples.size <= 3600, "Expected <= 3600 samples, got ${samples.size}")
        assertTrue(samples.size > 100, "Expected reasonable number of samples after downsampling")
    }

    @Test
    fun `downsampling preserves evenly spaced samples`() {
        val header = "date,time,speed"
        val totalSamples = 7200
        val rows = (0 until totalSamples).joinToString("\n") { i ->
            val hour = 10 + (i / 3600)
            val min = (i % 3600) / 60
            val sec = i % 60
            "2024-01-15,${hour.toString().padStart(2, '0')}:${min.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}.000,${i.toDouble()}"
        }
        val csv = "$header\n$rows"

        val samples = CsvParser.parse(csv)
        // First sample should be preserved
        assertEquals(0.0, samples[0].speedKmh, 0.01)
    }

    // ==================== Route Parsing ====================

    @Test
    fun `parseRoute returns empty list for non-GPS CSV`() {
        val header = "date,time,speed,voltage,phase_current,current,power,torque,pwm," +
            "battery_level,distance,totaldistance,system_temp,temp2,tilt,roll,mode,alert"
        val row = "2024-01-15,10:30:00.000,25.50,84.00,15.50,12.00,1500.00,10.00,45.67," +
            "85,1234,999999,35,28,2.50,-1.30,Sport,"
        val csv = "$header\n$row"

        val route = CsvParser.parseRoute(csv)
        assertTrue(route.isEmpty())
    }

    @Test
    fun `parseRoute returns empty list for empty input`() {
        assertEquals(emptyList(), CsvParser.parseRoute(""))
    }

    @Test
    fun `parseRoute returns empty list for header only`() {
        val header = "date,time,latitude,longitude,gps_speed,gps_alt,gps_heading,gps_distance," +
            "speed,voltage,phase_current,current,power,torque,pwm," +
            "battery_level,distance,totaldistance,system_temp,temp2,tilt,roll,mode,alert"
        assertEquals(emptyList(), CsvParser.parseRoute(header))
    }

    @Test
    fun `parseRoute extracts coordinates from GPS CSV`() {
        val header = "date,time,latitude,longitude,gps_speed,gps_alt,gps_heading,gps_distance," +
            "speed,voltage,phase_current,current,power,torque,pwm," +
            "battery_level,distance,totaldistance,system_temp,temp2,tilt,roll,mode,alert"
        val row = "2024-01-15,10:30:00.000,37.7749,-122.4194,20.00,50.50,90.00,1234," +
            "25.50,84.00,15.50,12.00,1500.00,10.00,45.67," +
            "85,100,999999,35,28,2.50,-1.30,Sport,"
        val csv = "$header\n$row"

        val route = CsvParser.parseRoute(csv)
        assertEquals(1, route.size)

        val p = route[0]
        assertEquals(37.7749, p.latitude, 0.0001)
        assertEquals(-122.4194, p.longitude, 0.0001)
        assertEquals(50.5, p.altitude, 0.01)
        assertEquals(90.0, p.bearing, 0.01)
        assertEquals(25.5, p.speedKmh, 0.01)
        assertEquals(20.0, p.gpsSpeedKmh, 0.01)
        assertTrue(p.timestampMs > 0)
    }

    @Test
    fun `parseRoute skips rows with zero coordinates`() {
        val header = "date,time,latitude,longitude,gps_speed,gps_alt,gps_heading,gps_distance," +
            "speed,voltage,phase_current,current,power,torque,pwm," +
            "battery_level,distance,totaldistance,system_temp,temp2,tilt,roll,mode,alert"
        val zeroRow = "2024-01-15,10:30:00.000,0.0,0.0,0.00,0.00,0.00,0," +
            "25.50,84.00,15.50,12.00,1500.00,10.00,45.67," +
            "85,100,999999,35,28,2.50,-1.30,Sport,"
        val goodRow = "2024-01-15,10:30:01.000,37.7749,-122.4194,20.00,50.50,90.00,100," +
            "30.00,83.00,10.00,8.00,1000.00,5.00,30.00," +
            "80,200,999999,34,27,1.50,-0.80,Sport,"
        val csv = "$header\n$zeroRow\n$goodRow"

        val route = CsvParser.parseRoute(csv)
        assertEquals(1, route.size)
        assertEquals(37.7749, route[0].latitude, 0.0001)
    }

    @Test
    fun `parseRoute skips rows with empty coordinate columns`() {
        val header = "date,time,latitude,longitude,gps_speed,gps_alt,gps_heading,gps_distance," +
            "speed,voltage,phase_current,current,power,torque,pwm," +
            "battery_level,distance,totaldistance,system_temp,temp2,tilt,roll,mode,alert"
        // GPS columns present but empty (GPS was enabled but no fix yet)
        val emptyGpsRow = "2024-01-15,10:30:00.000,,,,,," +
            "25.50,84.00,15.50,12.00,1500.00,10.00,45.67," +
            "85,100,999999,35,28,2.50,-1.30,Sport,"
        val goodRow = "2024-01-15,10:30:01.000,37.7749,-122.4194,20.00,50.50,90.00,100," +
            "30.00,83.00,10.00,8.00,1000.00,5.00,30.00," +
            "80,200,999999,34,27,1.50,-0.80,Sport,"
        val csv = "$header\n$emptyGpsRow\n$goodRow"

        val route = CsvParser.parseRoute(csv)
        assertEquals(1, route.size)
        assertEquals(37.7749, route[0].latitude, 0.0001)
    }

    @Test
    fun `parseRoute parses multiple points in order`() {
        val header = "date,time,latitude,longitude,gps_speed,gps_alt,gps_heading,gps_distance," +
            "speed,voltage,phase_current,current,power,torque,pwm," +
            "battery_level,distance,totaldistance,system_temp,temp2,tilt,roll,mode,alert"
        val row1 = "2024-01-15,10:30:00.000,37.7749,-122.4194,20.00,50.00,90.00,0," +
            "25.00,84.00,0.00,0.00,0.00,0.00,0.00,80,0,0,30,0,0.00,0.00,,"
        val row2 = "2024-01-15,10:30:01.000,37.7750,-122.4190,22.00,51.00,85.00,100," +
            "28.00,83.50,0.00,0.00,0.00,0.00,0.00,79,100,100,31,0,0.00,0.00,,"
        val row3 = "2024-01-15,10:30:02.000,37.7752,-122.4185,25.00,52.00,80.00,200," +
            "32.00,83.00,0.00,0.00,0.00,0.00,0.00,78,200,200,32,0,0.00,0.00,,"
        val csv = "$header\n$row1\n$row2\n$row3"

        val route = CsvParser.parseRoute(csv)
        assertEquals(3, route.size)
        assertEquals(37.7749, route[0].latitude, 0.0001)
        assertEquals(37.7750, route[1].latitude, 0.0001)
        assertEquals(37.7752, route[2].latitude, 0.0001)
        // Timestamps should be ordered and 1s apart
        assertEquals(1000, route[1].timestampMs - route[0].timestampMs)
    }

    @Test
    fun `parseRoute downsamples long routes`() {
        val header = "date,time,latitude,longitude,gps_speed,gps_alt,gps_heading,gps_distance," +
            "speed,voltage,phase_current,current,power,torque,pwm," +
            "battery_level,distance,totaldistance,system_temp,temp2,tilt,roll,mode,alert"
        val totalPoints = 7200
        val rows = (0 until totalPoints).joinToString("\n") { i ->
            val hour = 10 + (i / 3600)
            val min = (i % 3600) / 60
            val sec = i % 60
            val lat = 37.7749 + i * 0.0001
            val lon = -122.4194 + i * 0.00005
            "2024-01-15,${hour.toString().padStart(2, '0')}:${min.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}.000," +
                "$lat,$lon,20.00,50.00,90.00,$i," +
                "${i.toDouble()},84.00,0.00,0.00,0.00,0.00,0.00,80,0,0,30,0,0.00,0.00,,"
        }
        val csv = "$header\n$rows"

        val route = CsvParser.parseRoute(csv)
        assertTrue(route.size <= 3600, "Expected <= 3600 points, got ${route.size}")
        assertTrue(route.size > 100, "Expected reasonable number of points after downsampling")
    }

    // ==================== RouteBounds ====================

    @Test
    fun `RouteBounds from empty list returns null`() {
        val bounds = RouteBounds.from(emptyList())
        assertEquals(null, bounds)
    }

    @Test
    fun `RouteBounds from single point returns point as both min and max`() {
        val point = RoutePoint(
            timestampMs = 0L,
            latitude = 37.7749,
            longitude = -122.4194,
            altitude = 50.0,
            bearing = 90.0,
            speedKmh = 25.0,
            gpsSpeedKmh = 20.0
        )
        val bounds = RouteBounds.from(listOf(point))!!
        assertEquals(37.7749, bounds.minLatitude, 0.0001)
        assertEquals(37.7749, bounds.maxLatitude, 0.0001)
        assertEquals(-122.4194, bounds.minLongitude, 0.0001)
        assertEquals(-122.4194, bounds.maxLongitude, 0.0001)
    }

    @Test
    fun `RouteBounds from multiple points computes correct extents`() {
        val points = listOf(
            RoutePoint(0L, 37.7749, -122.4194, 50.0, 90.0, 25.0, 20.0),
            RoutePoint(1000L, 37.7800, -122.4100, 55.0, 85.0, 30.0, 25.0),
            RoutePoint(2000L, 37.7700, -122.4250, 45.0, 80.0, 20.0, 18.0),
        )
        val bounds = RouteBounds.from(points)!!
        assertEquals(37.7700, bounds.minLatitude, 0.0001)
        assertEquals(37.7800, bounds.maxLatitude, 0.0001)
        assertEquals(-122.4250, bounds.minLongitude, 0.0001)
        assertEquals(-122.4100, bounds.maxLongitude, 0.0001)
    }
}
