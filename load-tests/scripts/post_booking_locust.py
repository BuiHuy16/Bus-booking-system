
import os
import json
import random
from collections import deque

from locust import HttpUser, task, between
from locust.exception import StopUser

TOKEN = os.environ["BUSBOOKING_TEST_JWT"]
TRIP_IDS = json.loads(
    os.environ["BUSBOOKING_BOOKING_TRIP_IDS"]
)

# Mỗi Trip có đúng 4 lượt Booking
slots = [trip_id for trip_id in TRIP_IDS for _ in range(4)]
random.shuffle(slots)
queue = deque(slots)

class BookingLoadUser(HttpUser):
    wait_time = between(0.2, 0.6)

    @task
    def create_booking(self):
        try:
            trip_id = queue.popleft()
        except IndexError:
            raise StopUser()

        with self.client.post(
            "/api/bookings",
            json={
                "tripId": trip_id,
                "passengerName": "Kaggle Tester",
                "passengerPhone": "0901234567",
                "quantity": 1
            },
            headers={
                "Authorization": f"Bearer {TOKEN}"
            },
            name="POST /api/bookings",
            catch_response=True,
            timeout=15
        ) as response:
            if response.status_code == 201:
                response.success()
            else:
                response.failure(
                    f"HTTP {response.status_code}"
                )
