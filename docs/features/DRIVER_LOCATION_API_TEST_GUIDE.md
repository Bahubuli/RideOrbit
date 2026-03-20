# Driver Location API - Complete Test Guide

## Overview
This document provides a comprehensive guide for testing all Driver Location APIs with sample data.

## Prerequisites
1. Application must be running on `http://localhost:8080`
2. Redis server must be running (required for geospatial data storage)
3. PowerShell must be available (for running test scripts)

## Test File Location
- **Test Script**: `test_driver_location_api.ps1`

## Running Tests

### Option 1: Run PowerShell Script
```powershell
cd d:\Code\RideOrbit
.\test_driver_location_api.ps1
```

### Option 2: Manual Testing with cURL (Windows)
```powershell
# Update driver location
curl -X POST http://localhost:8080/api/v1/drivers/location/update `
  -H "Content-Type: application/json" `
  -d '{"driverId":1,"latitude":28.7041,"longitude":77.1025}'

# Get nearby drivers
curl -X GET "http://localhost:8080/api/v1/drivers/location/nearby?latitude=28.7041&longitude=77.1025&radiusInKm=5"

# Get all active drivers
curl -X GET http://localhost:8080/api/v1/drivers/location/active

# Check online status
curl -X GET http://localhost:8080/api/v1/drivers/location/1/online-status

# Remove driver location
curl -X DELETE http://localhost:8080/api/v1/drivers/location/1
```

## API Endpoints Details

### 1. Update Driver Location
- **Endpoint**: `POST /api/v1/drivers/location/update`
- **Parameters**: 
  - `driverId`: Long (required)
  - `latitude`: Double (required)
  - `longitude`: Double (required)
- **Sample Request**:
```json
{
  "driverId": 1,
  "latitude": 28.7041,
  "longitude": 77.1025
}
```
- **Expected Response**: 
```json
{
  "message": "Location updated successfully",
  "data": {
    "driverId": 1,
    "latitude": 28.7041,
    "longitude": 77.1025,
    "lastUpdated": "2026-03-18T10:30:45.123456"
  }
}
```

### 2. Get Specific Driver Location
- **Endpoint**: `GET /api/v1/drivers/location/{driverId}`
- **Path Parameters**: `driverId` (Long, required)
- **Expected Response**:
```json
{
  "driverId": 1,
  "latitude": 28.7041,
  "longitude": 77.1025,
  "lastUpdated": "2026-03-18T10:30:45.123456"
}
```

### 3. Find Nearby Drivers
- **Endpoint**: `GET /api/v1/drivers/location/nearby`
- **Query Parameters**:
  - `latitude`: Double (required)
  - `longitude`: Double (required)
  - `radiusInKm`: Double (optional, default: 5.0 km)
- **Expected Response**:
```json
{
  "nearbyDrivers": [
    {
      "driverId": 1,
      "latitude": 28.7041,
      "longitude": 77.1025,
      "distanceFromPoint": 0.5,
      "lastUpdated": "2026-03-18T10:30:45.123456"
    }
  ],
  "count": 1,
  "searchRadius": "5 km"
}
```

### 4. Get All Active Drivers
- **Endpoint**: `GET /api/v1/drivers/location/active`
- **Expected Response**:
```json
{
  "activeDrivers": [
    {
      "driverId": 1,
      "latitude": 28.7041,
      "longitude": 77.1025,
      "lastUpdated": "2026-03-18T10:30:45.123456"
    },
    {
      "driverId": 2,
      "latitude": 28.7050,
      "longitude": 77.1100,
      "lastUpdated": "2026-03-18T10:30:45.123456"
    }
  ],
  "totalCount": 2
}
```

### 5. Check Driver Online Status
- **Endpoint**: `GET /api/v1/drivers/location/{driverId}/online-status`
- **Path Parameters**: `driverId` (Long, required)
- **Expected Response**:
```json
{
  "driverId": 1,
  "isOnline": true
}
```

### 6. Remove Driver Location
- **Endpoint**: `DELETE /api/v1/drivers/location/{driverId}`
- **Path Parameters**: `driverId` (Long, required)
- **Expected Response**:
```json
{
  "message": "Driver location removed successfully"
}
```

## Sample Test Data
The test script includes 3 sample drivers with realistic locations:

### Driver 1
- ID: 1
- Location: 28.7041°N, 77.1025°E (Delhi, India)

### Driver 2
- ID: 2
- Location: 28.7050°N, 77.1100°E (Delhi, India - ~7km from Driver 1)

### Driver 3
- ID: 3
- Location: 28.6100°N, 77.0850°E (Delhi, India - ~11km from Driver 1)

## Test Scenarios Covered

1. ✓ Update driver location (multiple drivers)
2. ✓ Retrieve specific driver location
3. ✓ Find nearby drivers with various search radiuses (1km, 5km, 10km)
4. ✓ Get all active drivers
5. ✓ Check driver online status (online drivers)
6. ✓ Check offline driver status (non-existent driver)
7. ✓ Remove driver location (driver goes offline)
8. ✓ Verify driver removal

## Expected Behavior

### Success Cases
- Status Code: 200 OK
- Response contains expected data
- Nearby drivers are sorted by distance (ascending)

### Error Cases
- **Driver Not Found**: Returns 404 with ResourceNotFoundException message
- **Invalid Input**: Returns 400 (Bad Request)
- **Server Error**: Returns 500 with descriptive error message

## Debugging Tips

### If Tests Fail:
1. **Check Redis Connection**
   - Ensure Redis server is running on default port (6379)
   - Check Redis logs for connection errors

2. **Check Application Logs**
   - Look for Spring Boot startup logs
   - Check for dependency injection errors

3. **Validate Geospatial Queries**
   - Verify latitude and longitude are valid
   - Check that radius is positive

4. **Network Issues**
   - Ensure localhost:8080 is accessible
   - Check Windows Firewall settings

## Performance Considerations

- **Nearby Drivers Query**: Optimized using Redis GEORADIUS command
- **Response Time**: Should be <100ms for typical queries
- **Data Expiration**: Driver locations automatically expire after 6 hours

## Next Steps

After testing:
1. Review response times and optimize if needed
2. Test with higher volume of drivers (100+, 1000+)
3. Monitor Redis memory usage
4. Implement pagination for getAllActiveDrivers if dataset grows large
