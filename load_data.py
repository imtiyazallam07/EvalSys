import os
import sys
import json
import urllib.request
import subprocess

# ── Dependency check ─────────────────────────────────────────────────────────
# load_data.py uses only Python standard-library modules, so no third-party
# packages are required. If you extend this script to use libraries such as
# boto3, requests, google-cloud-storage, etc., add them to the list below so
# that users receive a clear installation hint instead of a cryptic ImportError.
_REQUIRED_PACKAGES = []   # e.g. ["requests", "boto3"]

_missing = []
for _pkg in _REQUIRED_PACKAGES:
    try:
        __import__(_pkg)
    except ImportError:
        _missing.append(_pkg)

if _missing:
    print("\n[ERROR] The following required Python packages are missing:")
    for _p in _missing:
        print(f"  - {_p}")
    print("\nInstall them all with:")
    print(f"  python -m pip install {' '.join(_missing)}")
    print()
    sys.exit(1)
# ─────────────────────────────────────────────────────────────────────────────


def load_env(env_path=".env"):
    env = {}
    if os.path.exists(env_path):
        try:
            with open(env_path, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if not line or line.startswith("#"):
                        continue
                    parts = line.split("=", 1)
                    if len(parts) == 2:
                        k = parts[0].strip()
                        v = parts[1].strip()
                        if (v.startswith('"') and v.endswith('"')) or (v.startswith("'") and v.endswith("'")):
                            v = v[1:-1]
                        env[k] = v
        except Exception as e:
            print(f"Error reading .env: {e}")
    return env

def download_config(config_url):
    print(f"Downloading config.json from {config_url} ...")
    try:
        with urllib.request.urlopen(config_url, timeout=10) as resp:
            data = resp.read()
        with open("config.json", "wb") as f_out:
            f_out.write(data)
        print("Saved config.json locally.")
        return True
    except Exception as e:
        print(f"Error downloading config.json: {e}")
        return False

def download_questions(question_json_url):
    print(f"Downloading question.json from {question_json_url} ...")
    try:
        with urllib.request.urlopen(question_json_url, timeout=10) as resp:
            data = resp.read().decode('utf-8')
        
        questions_map = json.loads(data)
        os.makedirs("qstns", exist_ok=True)
        
        for name, file_url in questions_map.items():
            if not file_url.startswith("http"):
                file_url = "http://" + file_url
            
            filename = file_url.split('/')[-1]
            if not filename.endswith('.csv'):
                filename += '.csv'
                
            target_path = os.path.join("qstns", filename)
            print(f"Downloading {filename} from {file_url} ...")
            with urllib.request.urlopen(file_url, timeout=10) as f_resp:
                csv_data = f_resp.read()
            with open(target_path, "wb") as f_out:
                f_out.write(csv_data)
            print(f"Saved {filename} to qstns/")
        return True
    except Exception as e:
        print(f"Error downloading questions: {e}")
        return False

def download_solution(solution_url):
    print(f"Downloading solution.json from {solution_url} ...")
    try:
        os.makedirs("solutions", exist_ok=True)
        target_path = os.path.join("solutions", "solution.json")
        with urllib.request.urlopen(solution_url, timeout=10) as resp:
            data = resp.read()
        with open(target_path, "wb") as f_out:
            f_out.write(data)
        print("Saved solution.json to solutions/.")
        return True
    except Exception as e:
        print(f"Error downloading solution.json: {e}")
        return False

def start_test(admin_mode=False):
    print("Starting the test...")
    jar_path = os.path.join("dist", "Computer_Based_Test.jar")
    if not os.path.exists(jar_path):
        print(f"Error: JAR file not found at {jar_path}")
        return
    
    try:
        cmd = ["java", "-jar", jar_path]
        if admin_mode:
            cmd.append("--admin-mode")
        subprocess.run(cmd)
    except Exception as e:
        print(f"Error starting Java application: {e}")

def main():
    env = load_env()
    pre_exist = env.get("PRE_EXIST", "false").lower() == "true"
    admin_mode = "--admin-mode" in sys.argv
    
    if pre_exist:
        print("PRE_EXIST is true: Downloading configurations, questions, and solutions...")
        
        config_link = env.get("CONFIG_LINK")
        question_link = env.get("QUESTION_LINK")
        solution_link = env.get("SOLUTION_LINK")
        
        if config_link:
            download_config(config_link)
        else:
            print("Warning: CONFIG_LINK not found in .env")
            
        if question_link:
            download_questions(question_link)
        else:
            print("Warning: QUESTION_LINK not found in .env")
            
        if not admin_mode:
            if solution_link:
                download_solution(solution_link)
            else:
                print("Warning: SOLUTION_LINK not found in .env")
            
    else:
        print("PRE_EXIST is false: Skipping downloads.")

    start_test(admin_mode)

if __name__ == "__main__":
    main()
