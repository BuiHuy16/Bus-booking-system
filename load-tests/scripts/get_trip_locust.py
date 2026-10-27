from locust import HttpUser, task, between
import os
TRIP_ID=os.environ['BUSBOOKING_TRIP_ID']
class TripReadUser(HttpUser):
    wait_time=between(0.2,0.6)
    @task(3)
    def list_trips(self):
        self.client.get('/api/trips',name='GET /api/trips')
    @task(1)
    def trip_detail(self):
        self.client.get(f'/api/trips/{TRIP_ID}',name='GET /api/trips/{id}')
