# RideOrbit API Testing Guide

## Quick Start

### Prerequisites
- Docker and Docker Compose installed
- Linux/WSL terminal (bash shell)
- curl installed

### Option 1: Full Automated Setup and Testing (Recommended)

```bash
cd /mnt/d/Code/RideOrbit
chmod +x setup_and_test.sh
bash setup_and_test.sh
```

This script will:
1. Start PostgreSQL and Redis Docker containers
2. Wait for both services to be healthy
3. Start the RideOrbit application
4. Run all 14 API tests
5. Display test results

### Option 2: Manual Setup

#### Step 1: Start Services
```bash
cd /mnt/d/Code/RideOrbit
docker-compose up -d
```

#### Step 2: Wait for Services (Optional but recommended)
```bash
# Check services are running
docker-compose ps

# Wait for health checks to pass
docker-compose exec -T postgres pg_isready -U rideorbit_user -d rideorbit_db
docker-compose exec -T redis redis-cli ping
```

#### Step 3: Start Application
```bash
export DB_URL="jdbc:postgresql://localhost:5432/rideorbit_db"
export DB_USERNAME="rideorbit_user"
export DB_PASSWORD="rideorbit_password"
export REDIS_HOST="localhost"
export REDIS_PORT="6379"

cd /mnt/d/Code/RideOrbit
java -jar build/libs/RideOrbit-0.0.1-SNAPSHOT.jar
```

#### Step 4: Run Tests (in a new terminal)
```bash
cd /mnt/d/Code/RideOrbit
chmod +x test_driver_location_api.sh
bash test_driver_location_api.sh
```

## Services

### PostgreSQL
- **Image**: postgres:15
- **Host**: localhost
- **Port**: 5432
- **Database**: rideorbit_db
- **Username**: rideorbit_user
- **Password**: rideorbit_password

### Redis
- **Image**: redis:7-alpine
- **Host**: localhost
- **Port**: 6379

## API Endpoints Tested

1. ✓ POST /api/v1/drivers/location/update - Update driver location
2. ✓ GET /api/v1/drivers/location/{driverId} - Get driver location
3. ✓ GET /api/v1/drivers/location/nearby - Find nearby drivers
4. ✓ GET /api/v1/drivers/location/active - Get all active drivers
5. ✓ GET /api/v1/drivers/location/{driverId}/online-status - Check online status
6. ✓ DELETE /api/v1/drivers/location/{driverId} - Remove driver location

## Cleaning Up

### Stop Services
```bash
docker-compose down
```

### Remove Volumes (Delete data)
```bash
docker-compose down -v
```

### Kill Running Application
```bash
pkill -f "java.*RideOrbit"
```

## Troubleshooting

### PostgreSQL Connection Refused
- Verify PostgreSQL container is running: `docker-compose ps`
- Check container logs: `docker-compose logs postgres`
- Restart: `docker-compose restart postgres`

### Redis Connection Issues
- Verify Redis container is running: `docker-compose ps`
- Check Redis logs: `docker-compose logs redis`
- Test connection: `docker-compose exec redis redis-cli ping`

### Application Won't Start
- Check logs: `tail -100 rideorbit.log`
- Ensure PostgreSQL and Redis are running
- Verify environment variables are set correctly
- Ensure port 8080 is available

### Application Started but Tests Fail
- Check application is responding: `curl http://localhost:8080/actuator/health`
- Verify API endpoint: `curl http://localhost:8080/api/v1/drivers/location/active`
- Check application logs: `docker-compose logs`

## Sample Test Data

The test script creates and tests with 3 sample drivers:

### Driver 1
- ID: 1
- Location: 28.7041°N, 77.1025°E (Delhi)
- Purpose: Base location for nearby searches

### Driver 2
- ID: 2
- Location: 28.7050°N, 77.1100°E (Delhi, ~7km away)
- Purpose: Within 10km radius search

### Driver 3
- ID: 3
- Location: 28.6100°N, 77.0850°E (Delhi, ~11km away)
- Purpose: Outside 5km radius, within 10km radius

## Test Coverage

### Positive Tests
- Create/update driver locations
- Retrieve specific driver details
- Find nearby drivers (multiple radius values)
- Get all active drivers
- Check online status for existing drivers
- Remove driver location

### Edge Cases
- Offline driver status check (non-existent driver)
- Verify driver removal through status check
- Different search radius values (1km, 5km, 10km)
- Distance-sorted results

## Expected Results

All 14 tests should pass with:
- Status Code: 200 OK for successful operations
- Status Code: 404 for not found errors
- Proper JSON responses with expected fields
- Drivers sorted by distance in nearby searches

## Next Steps

After successful testing:
1. Review application logs for any warnings
2. Test with higher volume of drivers
3. Monitor Redis memory usage
4. Test under load conditions
5. Implement pagination for large result sets
