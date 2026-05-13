import re

class SecretDetector:
    def __init__(self, text):
        self.text = text

    def detect(self):
        return {
            "firebase": self._find_firebase_keys(),
            "aws": self._find_aws_keys(),
            "tokens": self._find_bearer_tokens(),
            "jwt": self._find_jwt(),
            "stripe": self._find_stripe()
        }

    def _find_firebase_keys(self):
        pattern = r"AIza[0-9A-Za-z\-_]{35}"
        return re.findall(pattern, self.text)

    def _find_aws_keys(self):
        pattern = r"AKIA[0-9A-Z]{16}"
        return re.findall(pattern, self.text)

    def _find_bearer_tokens(self):
        pattern = r"Bearer\s+[A-Za-z0-9\.\-_]+"
        return re.findall(pattern, self.text)

    def _find_jwt(self):
        pattern = r"[A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]+"
        return re.findall(pattern, self.text)

    def _find_stripe(self):
        pattern = r"sk_live_[0-9a-zA-Z]{24,99}"
        return re.findall(pattern, self.text)
def detect_secrets_from_bytes(apk_bytes: bytes):
    """
    Extract text from APK byte content and detect secrets.
    For now this only searches inside the raw bytes.
    """
    try:
        text = apk_bytes.decode(errors="ignore")
    except:
        text = str(apk_bytes)

    detector = SecretDetector(text)
    return detector.detect()
