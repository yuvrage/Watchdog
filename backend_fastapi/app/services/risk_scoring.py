class RiskScoring:
    def __init__(self, permissions, endpoints, trackers, secrets):
        self.permissions = permissions
        self.endpoints = endpoints
        self.trackers = trackers
        self.secrets = secrets

    def calculate(self):
        score = 0

        # Permission risk
        dangerous = [
            "READ_SMS", "SEND_SMS", "READ_CONTACTS", "READ_CALL_LOG",
            "RECORD_AUDIO", "CAMERA", "ACCESS_FINE_LOCATION",
            "READ_PHONE_STATE", "SYSTEM_ALERT_WINDOW"
        ]
        for p in self.permissions:
            if any(d in p for d in dangerous):
                score += 4

        # Endpoint risk
        for ep in self.endpoints:
            if "http://" in ep:
                score += 4
            elif ":" in ep:  # IP or port
                score += 3
            elif ".ru" in ep:
                score += 5

        # Tracker risk
        score += len(self.trackers) * 2

        # Secrets
        for key, lst in self.secrets.items():
            score += len(lst) * 20

        # Clamp score
        if score > 100:
            score = 100

        level = "LOW"
        if score > 65: level = "HIGH"
        elif score > 30: level = "MEDIUM"

        return score, level
