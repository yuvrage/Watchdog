# backend_fastapi/app/services/apk_analyzer.py
import zipfile
import re
from urllib.parse import urlparse

# Try to import pyaxmlparser first (lightweight & Windows-friendly)
try:
    from pyaxmlparser import APK as PyAPK
except Exception:
    PyAPK = None

# Fallback to androguard only if available
try:
    from androguard.core.bytecodes.apk import APK as AndroAPK
except Exception:
    AndroAPK = None


URL_RE = re.compile(r"https?://[A-Za-z0-9\-\._~:/\?#\[\]@!$&'()*+,;=%]+")

KNOWN_TRACKERS = [
    "com.google.firebase", "com.google.android.gms", "com.facebook",
    "com.crashlytics", "com.appsflyer", "com.adjust", "com.onesignal",
    "com.mixpanel", "com.segment", "com.flurry", "com.moengage",
    "com.google.analytics", "com.urbanairship", "com.adcolony",
    "com.adobe.mobile", "com.amazonaws", "com.microsoft.appcenter"
]


def _extract_urls_from_bytes(data: bytes):
    try:
        text = data.decode("utf-8", errors="ignore")
    except Exception:
        text = str(data)
    found = set(m.group(0) for m in URL_RE.finditer(text))
    return list(found)


def _unique(iterable):
    seen = set()
    out = []
    for x in iterable:
        if x not in seen:
            seen.add(x)
            out.append(x)
    return out


def analyze_apk(file_path: str) -> dict:
    """
    Pure APK analysis.
    NO secret scanning.
    NO risk scoring.
    NO celery formatting.

    Returns:
    {
      "package": str,
      "app_name": str,
      "version_name": str,
      "permissions": [...],
      "activities": [...],
      "services": [...],
      "receivers": [...],
      "providers": [...],
      "endpoints": [...],
      "trackers": [...],
      "raw_errors": str|None
    }
    """

    result = {
        "package": None,
        "app_name": None,
        "version_name": None,
        "permissions": [],
        "activities": [],
        "services": [],
        "receivers": [],
        "providers": [],
        "endpoints": [],
        "trackers": [],
        "raw_errors": None,
    }

    # 1) Try pyaxmlparser
    if PyAPK is not None:
        try:
            apk = PyAPK(file_path)
            result["package"] = apk.package
            try:
                result["app_name"] = apk.get_app_name()
            except:
                pass
            try:
                result["version_name"] = apk.get_version_name()
            except:
                pass

            try:
                result["permissions"] = list(apk.get_permissions() or [])
            except:
                pass

            try:
                result["activities"] = list(apk.get_activities() or [])
            except:
                pass

            try:
                result["services"] = list(apk.get_services() or [])
            except:
                pass

            try:
                result["receivers"] = list(apk.get_receivers() or [])
            except:
                pass

            result["providers"] = []

        except Exception as e:
            result["raw_errors"] = f"pyaxmlparser failure: {e}"

    # 2) Fallback to androguard
    elif AndroAPK is not None:
        try:
            a = AndroAPK(file_path)
            result["package"] = a.get_package()
            result["app_name"] = a.get_app_name()
            try:
                result["version_name"] = a.get_androidversion_name()
            except:
                pass
            try:
                result["permissions"] = a.get_permissions() or []
            except:
                pass
            try:
                result["activities"] = a.get_activities() or []
            except:
                pass
            try:
                result["services"] = a.get_services() or []
            except:
                pass
            try:
                result["receivers"] = a.get_receivers() or []
            except:
                pass
            try:
                result["providers"] = a.get_providers() or []
            except:
                pass

        except Exception as e:
            result["raw_errors"] = f"androguard parsing failed: {e}"

    else:
        result["raw_errors"] = (
            "No APK parser installed. Install pyaxmlparser or androguard."
        )

    # 3) Extract endpoints + trackers from file contents
    try:
        urls = set()
        trackers = set()

        with zipfile.ZipFile(file_path, "r") as z:
            for name in z.namelist():
                if name.lower().endswith((".xml", ".arsc", ".dex", ".txt", ".smali", ".json", ".html")):
                    try:
                        data = z.read(name)
                    except:
                        continue

                    # URLs
                    for u in _extract_urls_from_bytes(data):
                        urls.add(u)

                    # Trackers
                    text = data.decode("utf-8", errors="ignore").lower()
                    for t in KNOWN_TRACKERS:
                        if t.lower() in text or t.split(".")[-1] in name.lower():
                            trackers.add(t)

        # Normalize URLs → take only domain where available
        domains = []
        for u in urls:
            try:
                host = urlparse(u).netloc
                domains.append(host if host else u)
            except:
                domains.append(u)

        result["endpoints"] = sorted(_unique(domains))
        result["trackers"] = sorted(_unique(trackers))

    except Exception as e:
        result["raw_errors"] = (result["raw_errors"] or "") + f" | zip error: {e}"

    # 4) Ensure lists are unique
    for key in ["permissions", "activities", "services", "receivers", "providers", "endpoints", "trackers"]:
        result[key] = sorted(_unique(result.get(key, [])))

    return result
