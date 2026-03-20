#!/usr/bin/env bash
# ============================================================
# RideOrbit API Test Script
# Tests all endpoints: Drivers, Passengers, Bookings, Driver Location
# Usage: ./test_api.sh [BASE_URL]
# Default BASE_URL: http://localhost:8080
# ============================================================

BASE_URL="${1:-http://localhost:8080}"
PASS=0
FAIL=0

# ── Colours ──────────────────────────────────────────────────
GREEN='\033[0;32m'
RED='\033[0;31m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
NC='\033[0m'

# ── Helpers ──────────────────────────────────────────────────
ok()   { echo -e "  ${GREEN}✓ PASS${NC} – $1"; ((PASS++)); }
fail() { echo -e "  ${RED}✗ FAIL${NC} – $1"; ((FAIL++)); }
section() { echo -e "\n${CYAN}══ $1 ══${NC}"; }

# Run curl and check expected HTTP status; return body
request() {
  local method="$1" url="$2" expected_status="$3" body="$4" label="$5"
  if [ -n "$body" ]; then
    response=$(curl -s -w "\n%{http_code}" -X "$method" \
      -H "Content-Type: application/json" \
      -d "$body" "$url")
  else
    response=$(curl -s -w "\n%{http_code}" -X "$method" "$url")
  fi
  status=$(echo "$response" | tail -1)
  body_out=$(echo "$response" | head -n -1)
  if [ "$status" -eq "$expected_status" ]; then
    ok "$label (HTTP $status)"
  else
    fail "$label – expected HTTP $expected_status, got HTTP $status"
    echo "       Response: $body_out"
  fi
  echo "$body_out"
}

# Wait until the app is reachable
wait_for_app() {
  echo -e "${YELLOW}Waiting for app at $BASE_URL ...${NC}"
  for i in $(seq 1 30); do
    if curl -s --max-time 3 "$BASE_URL/api/drivers" > /dev/null 2>&1; then
      echo -e "${GREEN}App is up!${NC}\n"
      return 0
    fi
    echo -n "."
    sleep 3
  done
  echo -e "\n${RED}App did not start in time. Aborting.${NC}"
  exit 1
}

# ── Start ─────────────────────────────────────────────────────
echo -e "${CYAN}============================================${NC}"
echo -e "${CYAN}  RideOrbit API Tests  –  $BASE_URL${NC}"
echo -e "${CYAN}============================================${NC}"
wait_for_app

# ════════════════════════════════════════════════════════════
section "1. DRIVER ENDPOINTS"
# ════════════════════════════════════════════════════════════

# Create Driver 1
echo -e "\n${YELLOW}[POST] Create Driver 1${NC}"
D1=$(request POST "$BASE_URL/api/drivers" 201 \
  '{"name":"Arjun Sharma","email":"arjun@rideorbit.com","phoneNumber":"9876543210","licenseNumber":"DL-001","vehicleModel":"Swift Dzire","vehiclePlateNumber":"MH01AB1234","isAvailable":true}' \
  "Create Driver 1")
DRIVER1_ID=$(echo "$D1" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
echo "       Driver 1 ID: $DRIVER1_ID"

# Create Driver 2
echo -e "\n${YELLOW}[POST] Create Driver 2${NC}"
D2=$(request POST "$BASE_URL/api/drivers" 201 \
  '{"name":"Priya Singh","email":"priya@rideorbit.com","phoneNumber":"9123456789","licenseNumber":"DL-002","vehicleModel":"Honda City","vehiclePlateNumber":"DL02CD5678","isAvailable":true}' \
  "Create Driver 2")
DRIVER2_ID=$(echo "$D2" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
echo "       Driver 2 ID: $DRIVER2_ID"

# Get all drivers
echo -e "\n${YELLOW}[GET] Get all drivers${NC}"
request GET "$BASE_URL/api/drivers" 200 "" "Get all drivers" > /dev/null

# Get driver by ID
echo -e "\n${YELLOW}[GET] Get driver by ID${NC}"
request GET "$BASE_URL/api/drivers/$DRIVER1_ID" 200 "" "Get driver by ID ($DRIVER1_ID)" > /dev/null

# Get driver by email
echo -e "\n${YELLOW}[GET] Get driver by email${NC}"
request GET "$BASE_URL/api/drivers/email/arjun@rideorbit.com" 200 "" "Get driver by email" > /dev/null

# Get available drivers
echo -e "\n${YELLOW}[GET] Get available drivers${NC}"
request GET "$BASE_URL/api/drivers/available" 200 "" "Get available drivers" > /dev/null

# Update driver
echo -e "\n${YELLOW}[PUT] Update driver${NC}"
request PUT "$BASE_URL/api/drivers/$DRIVER1_ID" 200 \
  '{"name":"Arjun Sharma Updated","email":"arjun@rideorbit.com","phoneNumber":"9876543210","licenseNumber":"DL-001","vehicleModel":"Maruti Ertiga","vehiclePlateNumber":"MH01AB1234","isAvailable":false}' \
  "Update driver $DRIVER1_ID" > /dev/null

# ════════════════════════════════════════════════════════════
section "2. PASSENGER ENDPOINTS"
# ════════════════════════════════════════════════════════════

# Create Passenger 1
echo -e "\n${YELLOW}[POST] Create Passenger 1${NC}"
P1=$(request POST "$BASE_URL/api/passengers" 201 \
  '{"name":"Riya Mehta","email":"riya@example.com","phoneNumber":"8899001122"}' \
  "Create Passenger 1")
PASS1_ID=$(echo "$P1" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
echo "       Passenger 1 ID: $PASS1_ID"

# Create Passenger 2
echo -e "\n${YELLOW}[POST] Create Passenger 2${NC}"
P2=$(request POST "$BASE_URL/api/passengers" 201 \
  '{"name":"Vikram Patel","email":"vikram@example.com","phoneNumber":"7711223344"}' \
  "Create Passenger 2")
PASS2_ID=$(echo "$P2" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
echo "       Passenger 2 ID: $PASS2_ID"

# Get all passengers
echo -e "\n${YELLOW}[GET] Get all passengers${NC}"
request GET "$BASE_URL/api/passengers" 200 "" "Get all passengers" > /dev/null

# Get passenger by ID
echo -e "\n${YELLOW}[GET] Get passenger by ID${NC}"
request GET "$BASE_URL/api/passengers/$PASS1_ID" 200 "" "Get passenger by ID ($PASS1_ID)" > /dev/null

# Get passenger by email
echo -e "\n${YELLOW}[GET] Get passenger by email${NC}"
request GET "$BASE_URL/api/passengers/email/riya@example.com" 200 "" "Get passenger by email" > /dev/null

# Update passenger
echo -e "\n${YELLOW}[PUT] Update passenger${NC}"
request PUT "$BASE_URL/api/passengers/$PASS1_ID" 200 \
  '{"name":"Riya Mehta Updated","email":"riya@example.com","phoneNumber":"8899001122"}' \
  "Update passenger $PASS1_ID" > /dev/null

# ════════════════════════════════════════════════════════════
section "3. BOOKING ENDPOINTS"
# ════════════════════════════════════════════════════════════

# Need an available driver — use DRIVER2_ID (still available)
echo -e "\n${YELLOW}[POST] Create Booking 1${NC}"
B1=$(request POST "$BASE_URL/api/bookings" 201 \
  "{\"passengerId\":$PASS1_ID,\"driverId\":$DRIVER2_ID,\"pickupLocation\":\"Andheri East, Mumbai\",\"dropoffLocation\":\"Bandra West, Mumbai\",\"fare\":250.50}" \
  "Create Booking 1")
BOOK1_ID=$(echo "$B1" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
echo "       Booking 1 ID: $BOOK1_ID"

echo -e "\n${YELLOW}[POST] Create Booking 2${NC}"
B2=$(request POST "$BASE_URL/api/bookings" 201 \
  "{\"passengerId\":$PASS2_ID,\"pickupLocation\":\"Powai, Mumbai\",\"dropoffLocation\":\"Dadar, Mumbai\",\"fare\":180.00}" \
  "Create Booking 2")
BOOK2_ID=$(echo "$B2" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
echo "       Booking 2 ID: $BOOK2_ID"

# Get all bookings
echo -e "\n${YELLOW}[GET] Get all bookings${NC}"
request GET "$BASE_URL/api/bookings" 200 "" "Get all bookings" > /dev/null

# Get booking by ID
echo -e "\n${YELLOW}[GET] Get booking by ID${NC}"
request GET "$BASE_URL/api/bookings/$BOOK1_ID" 200 "" "Get booking by ID ($BOOK1_ID)" > /dev/null

# Get bookings by passenger
echo -e "\n${YELLOW}[GET] Get bookings by passenger${NC}"
request GET "$BASE_URL/api/bookings/passenger/$PASS1_ID" 200 "" "Get bookings by passenger $PASS1_ID" > /dev/null

# Get bookings by driver
echo -e "\n${YELLOW}[GET] Get bookings by driver${NC}"
request GET "$BASE_URL/api/bookings/driver/$DRIVER2_ID" 200 "" "Get bookings by driver $DRIVER2_ID" > /dev/null

# Update booking status
echo -e "\n${YELLOW}[PATCH] Update booking status → CONFIRMED${NC}"
request PATCH "$BASE_URL/api/bookings/$BOOK1_ID/status?status=CONFIRMED" 200 "" \
  "Update booking $BOOK1_ID status to CONFIRMED" > /dev/null

echo -e "\n${YELLOW}[PATCH] Update booking status → IN_PROGRESS${NC}"
request PATCH "$BASE_URL/api/bookings/$BOOK1_ID/status?status=IN_PROGRESS" 200 "" \
  "Update booking $BOOK1_ID status to IN_PROGRESS" > /dev/null

echo -e "\n${YELLOW}[PATCH] Update booking status → COMPLETED${NC}"
request PATCH "$BASE_URL/api/bookings/$BOOK1_ID/status?status=COMPLETED" 200 "" \
  "Update booking $BOOK1_ID status to COMPLETED" > /dev/null

# Update booking body
echo -e "\n${YELLOW}[PUT] Update booking${NC}"
request PUT "$BASE_URL/api/bookings/$BOOK2_ID" 200 \
  "{\"passengerId\":$PASS2_ID,\"pickupLocation\":\"Powai Lake, Mumbai\",\"dropoffLocation\":\"Dadar TT, Mumbai\",\"fare\":200.00}" \
  "Update booking $BOOK2_ID" > /dev/null

# ════════════════════════════════════════════════════════════
section "4. DRIVER LOCATION ENDPOINTS"
# ════════════════════════════════════════════════════════════

# Update location – Driver 1 (near Mumbai CST)
echo -e "\n${YELLOW}[POST] Update Driver 1 location${NC}"
request POST "$BASE_URL/api/v1/drivers/location/update" 200 \
  "{\"driverId\":$DRIVER1_ID,\"latitude\":18.9398,\"longitude\":72.8354}" \
  "Update location for driver $DRIVER1_ID" > /dev/null

# Update location – Driver 2 (near Bandra)
echo -e "\n${YELLOW}[POST] Update Driver 2 location${NC}"
request POST "$BASE_URL/api/v1/drivers/location/update" 200 \
  "{\"driverId\":$DRIVER2_ID,\"latitude\":19.0596,\"longitude\":72.8295}" \
  "Update location for driver $DRIVER2_ID" > /dev/null

# Get specific driver location
echo -e "\n${YELLOW}[GET] Get driver location${NC}"
request GET "$BASE_URL/api/v1/drivers/location/$DRIVER1_ID" 200 "" \
  "Get location for driver $DRIVER1_ID" > /dev/null

# Get all active drivers
echo -e "\n${YELLOW}[GET] Get active drivers${NC}"
request GET "$BASE_URL/api/v1/drivers/location/active" 200 "" \
  "Get all active drivers" > /dev/null

# Get nearby drivers (search from Andheri, radius 20km)
echo -e "\n${YELLOW}[GET] Get nearby drivers${NC}"
request GET "$BASE_URL/api/v1/drivers/location/nearby?latitude=19.1136&longitude=72.8697&radiusInKm=20" 200 "" \
  "Get nearby drivers within 20km of Andheri" > /dev/null

# Check driver online status
echo -e "\n${YELLOW}[GET] Check driver online status${NC}"
request GET "$BASE_URL/api/v1/drivers/location/$DRIVER1_ID/online-status" 200 "" \
  "Check online status for driver $DRIVER1_ID" > /dev/null

# Remove driver location
echo -e "\n${YELLOW}[DELETE] Remove driver location${NC}"
request DELETE "$BASE_URL/api/v1/drivers/location/$DRIVER2_ID" 200 "" \
  "Remove location for driver $DRIVER2_ID" > /dev/null

# ════════════════════════════════════════════════════════════
section "5. CLEANUP (Delete)"
# ════════════════════════════════════════════════════════════

echo -e "\n${YELLOW}[DELETE] Delete Booking 2${NC}"
request DELETE "$BASE_URL/api/bookings/$BOOK2_ID" 204 "" "Delete booking $BOOK2_ID" > /dev/null

echo -e "\n${YELLOW}[DELETE] Delete Driver 1${NC}"
request DELETE "$BASE_URL/api/drivers/$DRIVER1_ID" 204 "" "Delete driver $DRIVER1_ID" > /dev/null

echo -e "\n${YELLOW}[DELETE] Delete Passenger 2${NC}"
request DELETE "$BASE_URL/api/passengers/$PASS2_ID" 204 "" "Delete passenger $PASS2_ID" > /dev/null

# ════════════════════════════════════════════════════════════
section "6. VALIDATION TESTS (expect 4xx)"
# ════════════════════════════════════════════════════════════

echo -e "\n${YELLOW}[GET] Get non-existent driver (404)${NC}"
request GET "$BASE_URL/api/drivers/999999" 404 "" "Get non-existent driver" > /dev/null

echo -e "\n${YELLOW}[POST] Create driver with missing required fields (400)${NC}"
request POST "$BASE_URL/api/drivers" 400 \
  '{"email":"bad-email","phoneNumber":""}' \
  "Create driver with invalid data" > /dev/null

echo -e "\n${YELLOW}[POST] Update driver location with invalid coordinates (400)${NC}"
request POST "$BASE_URL/api/v1/drivers/location/update" 400 \
  '{"driverId":1,"latitude":999,"longitude":999}' \
  "Update location with invalid coords" > /dev/null

# ════════════════════════════════════════════════════════════
# Summary
# ════════════════════════════════════════════════════════════
TOTAL=$((PASS + FAIL))
echo -e "\n${CYAN}════════════════════════════════════════${NC}"
echo -e "${CYAN}  RESULTS: $PASS/$TOTAL passed${NC}"
if [ "$FAIL" -gt 0 ]; then
  echo -e "  ${RED}$FAIL test(s) FAILED${NC}"
else
  echo -e "  ${GREEN}All tests passed!${NC}"
fi
echo -e "${CYAN}════════════════════════════════════════${NC}\n"

exit $FAIL
