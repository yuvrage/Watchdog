# app/services/risk_engine.py

def calculate_risk_score(apk, secrets, mode="bevigil"):
    """
    mode = "bevigil"  → Soft scoring (matches Bevigil)
    mode = "strict"   → Your previous detailed scoring
    """

    if mode == "strict":
        return _strict_score(apk, secrets)

    return _bevigil_score(apk, secrets)


# -----------------------------------------------------------
# -----------------------------------------------------------
def _bevigil_score(apk, secrets):

    permissions = apk.get("permissions", [])
    trackers = apk.get("trackers", [])
    endpoints = apk.get("endpoints", [])

    # -----------------------------
    # 1) Permission scoring (SOFT)
    # -----------------------------
    dangerous = [
        "READ_SMS","READ_CONTACTS","ACCESS_FINE_LOCATION",
        "RECORD_AUDIO","CALL_PHONE","CAMERA",
        "SYSTEM_ALERT_WINDOW","ANSWER_PHONE_CALLS",
        "READ_PHONE_STATE"
    ]

    count = sum(1 for p in permissions if p.upper() in [d.upper() for d in dangerous])

    if count == 0:
        perm_score = 0
    elif count <= 3:
        perm_score = 2
    elif count <= 7:
        perm_score = 4
    else:
        perm_score = 6    # MAX (Bevigil uses small weights)

    # -----------------------------
    # 2) Tracker scoring (SOFT)
    # -----------------------------
    if len(trackers) == 0:
        tracker_score = 0
    elif len(trackers) <= 3:
        tracker_score = 2
    else:
        tracker_score = 4  # Bevigil rarely gives > 4 for trackers

    # -----------------------------
    # 3) Endpoint scoring (SOFT)
    # -----------------------------
    risky_endpoints = 0
    for e in endpoints:
        if e.startswith("http://"):
            risky_endpoints += 1
        if any(ip in e for ip in ["10.", "127.", "192.168."]):
            risky_endpoints += 1

    if risky_endpoints == 0:
        endpoint_score = 0
    elif risky_endpoints <= 3:
        endpoint_score = 3
    else:
        endpoint_score = 5  # MAX

    # -----------------------------
    # 4) Secret scoring (SOFT)
    # -----------------------------
    secret_score = 0
    # Bevigil-friendly secret scoring
    secret_score = 0

    if secrets.get("tokens"):
     secret_score += 1
    if secrets.get("firebase"):
     secret_score += 1
    if secrets.get("aws"):
     secret_score += 1
    if secrets.get("jwt"):
     secret_score += 1


    secret_score = min(secret_score, 3)


    # -----------------------------
    # -----------------------------
    total = perm_score + tracker_score + endpoint_score + secret_score
    
    # Scale to 0-10 range (max possible is 6+4+5+3=18)
    # Linear scaling: map 0-18 to 0-10
    scaled_score = (total / 18.0) * 10.0
    
    # Determine risk level based on scaled score (matching BeVigil thresholds)
    # Only apps with very high risk scores (8.0+) are marked DANGEROUS
    if scaled_score <= 3.0:  # 0-3.0 (roughly 0-5.4 raw) - Most apps are SAFE
        level = "SAFE"
    elif scaled_score <= 8.0:  # 3.1-8.0 (roughly 5.5-14.4 raw) - Most risky apps are MODERATE
        level = "MODERATE"
    else:  # 8.1-10 (roughly 14.5-18 raw) - Only extreme cases are DANGEROUS
        level = "DANGEROUS"
    
    # Ensure score is between 0-10
    scaled_score = max(0.0, min(10.0, scaled_score))

    return {
        "risk_score": round(scaled_score, 1),  # Round to 1 decimal place like BeVigil
        "risk_level": level,
        "components": {
            "permissions_score": perm_score,
            "tracker_score": tracker_score,
            "endpoint_score": endpoint_score,
            "secret_score": secret_score
        }
    }


# -----------------------------------------------------------
# 🔥 STRICT mode scoring (your existing high score method)
# -----------------------------------------------------------
def _strict_score(apk, secrets):
    # (Your previous intensity-based scoring)
    # We keep this intact, so your engine supports both modes.
    permissions = apk.get("permissions", [])
    endpoints = apk.get("endpoints", [])
    trackers = apk.get("trackers", [])

    dangerous = [
        "ACCESS_FINE_LOCATION","ACCESS_COARSE_LOCATION","READ_SMS",
        "RECORD_AUDIO","CAMERA","CALL_PHONE","READ_CONTACTS"
    ]

    score = 0
    for p in permissions:
        if any(d in p.upper() for d in dangerous):
            score += 4

    for e in endpoints:
        if e.startswith("http://"):
            score += 4
        elif any(x in e for x in ["10.", "192.168.", "127."]):
            score += 2

    score += len(trackers) * 2

    if secrets.get("firebase"):
        score += 15
    if secrets.get("aws"):
        score += 20
    if secrets.get("stripe"):
        score += 25

    score = min(score, 100)

    if score <= 30:
        level = "LOW"
    elif score <= 65:
        level = "MEDIUM"
    else:
        level = "HIGH"

    return {
        "risk_score": score,
        "risk_level": level,
        "components": {}
    }
