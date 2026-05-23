import json
import csv
import os
import sys
import shutil
from datetime import datetime

# ── Dependency check ─────────────────────────────────────────────────────────
# Add any third-party package names here.  The Dropbox SDK is needed when
# DROPBOX_APP_KEY / DROPBOX_APP_SECRET / DROPBOX_REFRESH_TOKEN are configured
# in .env and the evaluator is set to upload reports automatically.
_REQUIRED_PACKAGES = ["dropbox"]

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


# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------
EVAL_DIR = os.path.dirname(os.path.abspath(__file__))

if len(sys.argv) > 1:
    CBT_DIR = os.path.abspath(sys.argv[1])
else:
    CBT_DIR = r"C:\CBT"

RESPONSE_FILE   = os.path.join(CBT_DIR,  "response", "responses.json")
EXAMINEE_FILE   = os.path.join(CBT_DIR,  "examinee_details.json")
REPORT_DIR      = os.path.join(CBT_DIR,  "report")
SOLUTIONS_DIR   = os.path.join(EVAL_DIR, "solutions")
ANSWER_KEY_FILE = os.path.join(SOLUTIONS_DIR, "solution.json")

# config.json lives in CBT_DIR (written/downloaded by Main.java at startup)
CONFIG_FILE     = os.path.join(CBT_DIR, "config.json")

# ---------------------------------------------------------------------------
# Load .env configuration
# ---------------------------------------------------------------------------
def load_env():
    env_paths = [
        os.path.join(CBT_DIR, ".env"),
        os.path.join(EVAL_DIR, ".env"),
        ".env"
    ]
    env = {}
    for path in env_paths:
        if os.path.exists(path):
            try:
                with open(path, "r", encoding="utf-8") as f:
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
                break
            except Exception as e:
                print(f"Error reading .env at {path}: {e}")
    return env

ENV = load_env()

# ---------------------------------------------------------------------------
# Scoring
# ---------------------------------------------------------------------------
MARKS_CORRECT   =  1
MARKS_INCORRECT =  0
GRACE_MARK      =  1
OPTION_MAP      = {1: "a", 2: "b", 3: "c", 4: "d"}

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
def load_json(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)

def option_text(entry, opt_int):
    letter = OPTION_MAP.get(opt_int)
    return entry.get(letter, "") if letter else ""

def load_examinee():
    if not os.path.exists(EXAMINEE_FILE):
        print(f"  [INFO] examinee_details.json not found - fields will be blank.")
        return {"name": "", "section": "", "registrationNumber": "",
                "startTime": "", "endTime": ""}
    return load_json(EXAMINEE_FILE)

# ---------------------------------------------------------------------------
# Solution download
# ---------------------------------------------------------------------------

def get_solution_url():
    """
    Reads the 'solution' URL from config.json in CBT_DIR.
    Returns an empty string if the file or key is missing.
    """
    if not os.path.exists(CONFIG_FILE):
        print(f"  [Config] config.json not found at {CONFIG_FILE}")
        return ""
    try:
        cfg = load_json(CONFIG_FILE)
        url = cfg.get("solution", "").strip()
        if url:
            print(f"  [Config] Solution URL: {url}")
        else:
            print("  [Config] 'solution' key missing or empty in config.json")
        return url
    except Exception as e:
        print(f"  [Config] Could not parse config.json: {e}")
        return ""


def download_solution():
    """
    Downloads solution.json from the URL stored in config.json or .env, and writes it
    to SOLUTIONS_DIR/solution.json.
    Returns True on success, False otherwise.
    """
    if ENV.get("PRE_EXIST", "false").lower() == "true":
        print("  [Solution] PRE_EXIST is true — skipping download, using existing solution.json.")
        return os.path.exists(ANSWER_KEY_FILE)

    url = ENV.get("SOLUTION_LINK", "").strip() or get_solution_url()
    if not url:
        print("  [Solution] No URL — using existing solution.json if present.")
        return os.path.exists(ANSWER_KEY_FILE)

    import urllib.request
    import urllib.error

    os.makedirs(SOLUTIONS_DIR, exist_ok=True)
    try:
        print(f"  [Solution] Downloading from {url} …")
        with urllib.request.urlopen(url, timeout=10) as resp:
            data = resp.read()
        with open(ANSWER_KEY_FILE, "wb") as f:
            f.write(data)
        print(f"  [Solution] Saved → {ANSWER_KEY_FILE}")
        return True
    except urllib.error.HTTPError as e:
        print(f"  [Solution] HTTP {e.code}: {e.reason}")
    except urllib.error.URLError as e:
        print(f"  [Solution] Network error: {e.reason}")
    except Exception as e:
        print(f"  [Solution] Download failed: {e}")
    return False

# ---------------------------------------------------------------------------
# Per-subject evaluation
# ---------------------------------------------------------------------------
def evaluate_subject(subject, student_answers, correct_answers, solutions):
    total = attempted = correct = grace = 0
    rows = []

    for qid_str, student_int in student_answers.items():
        if qid_str not in correct_answers:
            print(f"  [WARN] Q-ID {qid_str} not in solution.json [{subject}] - skipping")
            continue

        correct_int   = correct_answers[qid_str]
        entry         = solutions.get(qid_str, {})
        question_text = entry.get("Question", f"Q-ID {qid_str}")

        if correct_int == 0:                          # cancelled question
            grace += 1
            total += 1
            rows.append({
                "qid": qid_str, "question": question_text,
                "correct": "N/A (Cancelled)",
                "student": option_text(entry, student_int) if student_int != 0 else "Not Attempted",
                "result": "Grace +1"
            })
            continue

        correct_text = option_text(entry, correct_int)
        if student_int == 0:
            student_text, result = "Not Attempted", "Skipped"
        else:
            attempted += 1
            student_text = option_text(entry, student_int)
            if student_int == correct_int:
                correct += 1
                result = "Correct"
            else:
                result = "Wrong"

        total += 1
        rows.append({
            "qid": qid_str, "question": question_text,
            "correct": correct_text, "student": student_text, "result": result
        })

    marks = correct * MARKS_CORRECT + (attempted - correct) * MARKS_INCORRECT + grace * GRACE_MARK
    print(f"  [{subject}] {correct}/{total - grace} correct + {grace} grace = {marks} marks")
    return {
        "subject": subject, "rows": rows,
        "total": total, "attempted": attempted, "correct": correct,
        "wrong": attempted - correct, "skipped": total - attempted - grace,
        "grace": grace, "marks": marks
    }

# ---------------------------------------------------------------------------
# CSV writer
# ---------------------------------------------------------------------------
def write_csv(data, student_info):
    os.makedirs(REPORT_DIR, exist_ok=True)
    out_path = os.path.join(REPORT_DIR, f"{data['subject']}_report.csv")
    with open(out_path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["Name",                student_info.get("name", "")])
        w.writerow(["Section",             student_info.get("section", "")])
        w.writerow(["Registration Number", student_info.get("registrationNumber", "")])
        w.writerow([])
        w.writerow(["Q_ID", "Question", "Correct Option", "Student Option", "Result"])
        for r in data["rows"]:
            w.writerow([r["qid"], r["question"], r["correct"], r["student"], r["result"]])
        w.writerow([])
        w.writerow([
            f"Marks: {data['marks']} / {data['total']}",
            f"Attempted: {data['attempted']}",
            f"Correct: {data['correct']}",
            f"Wrong: {data['wrong']}",
            f"Skipped: {data['skipped']}",
            f"Grace: {data['grace']}"
        ])
    return out_path

# ---------------------------------------------------------------------------
# Dropbox config
# ---------------------------------------------------------------------------
DROPBOX_APP_KEY       = ENV.get("DROPBOX_APP_KEY")
DROPBOX_APP_SECRET    = ENV.get("DROPBOX_APP_SECRET")
DROPBOX_REFRESH_TOKEN = ENV.get("DROPBOX_REFRESH_TOKEN")
DROPBOX_UPLOAD_FOLDER = "/CBT_Reports"

# ---------------------------------------------------------------------------
# Dropbox uploader
# ---------------------------------------------------------------------------
def get_dropbox_access_token():
    import urllib.request, urllib.parse, base64
    data = urllib.parse.urlencode({
        "grant_type":    "refresh_token",
        "refresh_token": DROPBOX_REFRESH_TOKEN,
    }).encode()
    creds_b64 = base64.b64encode(
        f"{DROPBOX_APP_KEY}:{DROPBOX_APP_SECRET}".encode()).decode()
    req = urllib.request.Request(
        "https://api.dropboxapi.com/oauth2/token",
        data=data,
        headers={"Authorization": f"Basic {creds_b64}"},
        method="POST"
    )
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode())["access_token"]


def upload_to_dropbox(local_path, filename):
    """
    Upload a file to Dropbox.
    Returns True on success, False on failure.
    """
    import urllib.request, urllib.error

    try:
        access_token = get_dropbox_access_token()
    except Exception as e:
        print(f"  [Dropbox] Could not get access token: {e}")
        return False

    dropbox_path = f"{DROPBOX_UPLOAD_FOLDER}/{filename}"
    with open(local_path, "rb") as f:
        data = f.read()

    headers = {
        "Authorization":   f"Bearer {access_token}",
        "Content-Type":    "application/octet-stream",
        "Dropbox-API-Arg": json.dumps({
            "path":       dropbox_path,
            "mode":       "overwrite",
            "autorename": False,
            "mute":       False
        })
    }
    req = urllib.request.Request(
        "https://content.dropboxapi.com/2/files/upload",
        data=data, headers=headers, method="POST"
    )
    try:
        with urllib.request.urlopen(req) as resp:
            result = json.loads(resp.read().decode())
            print(f"  [Dropbox] Uploaded → {result.get('path_display', dropbox_path)}")
            return True
    except urllib.error.HTTPError as e:
        print(f"  [Dropbox] Upload failed ({e.code}): {e.read().decode()}")
    except urllib.error.URLError as e:
        print(f"  [Dropbox] Network error: {e.reason}")
    return False

# ---------------------------------------------------------------------------
# Cleanup — only called after confirmed Dropbox upload
# ---------------------------------------------------------------------------

def cleanup_cbt_folder():
    """
    Deletes:
      • CBT_DIR/qstns/          (question CSVs)
      • CBT_DIR/response/       (responses.json)
      • CBT_DIR/examinee_details.json
      • EVAL_DIR/solutions/     (solution JSONs, including downloaded solution.json)
    Resets the system to its initial state so it is ready for the next examinee.
    """
    targets = [
        os.path.join(CBT_DIR,  "qstns"),
        os.path.join(CBT_DIR,  "response"),
        os.path.join(CBT_DIR,  "examinee_details.json"),
        SOLUTIONS_DIR,
    ]
    for target in targets:
        try:
            if os.path.isdir(target):
                shutil.rmtree(target)
                print(f"  [Cleanup] Removed directory: {target}")
            elif os.path.isfile(target):
                os.remove(target)
                print(f"  [Cleanup] Removed file: {target}")
            else:
                print(f"  [Cleanup] Not found (already clean): {target}")
        except Exception as e:
            print(f"  [Cleanup] Could not remove {target}: {e}")


# ---------------------------------------------------------------------------
# PDF writer
# ---------------------------------------------------------------------------
def write_pdf(all_data, student_info):
    from reportlab.lib.pagesizes import A4
    from reportlab.lib import colors
    from reportlab.lib.units import mm
    from reportlab.lib.styles import ParagraphStyle
    from reportlab.platypus import (SimpleDocTemplate, Paragraph, Spacer,
                                    Table, TableStyle, HRFlowable, PageBreak)
    from reportlab.lib.enums import TA_CENTER, TA_LEFT, TA_RIGHT

    section = student_info.get("section", "Unknown").strip()
    reg     = student_info.get("registrationNumber", "Unknown").strip()
    name    = student_info.get("name", "Unknown").strip()
    name_dashed = "-".join(name.split())

    def safe(s):
        return "".join(c for c in s if c.isalnum() or c in "-_")

    pdf_filename = f"{safe(section)}_{safe(reg)}_{safe(name_dashed)}.pdf"

    student_data_dir = os.path.join(EVAL_DIR, "Student_data")
    os.makedirs(student_data_dir, exist_ok=True)
    pdf_path = os.path.join(student_data_dir, pdf_filename)

    doc = SimpleDocTemplate(
        pdf_path, pagesize=A4,
        leftMargin=15*mm, rightMargin=15*mm,
        topMargin=15*mm, bottomMargin=15*mm
    )

    C_DARK   = colors.HexColor("#1a1a2e")
    C_ACCENT = colors.HexColor("#16213e")
    C_BLUE   = colors.HexColor("#0f3460")
    C_LIGHT  = colors.HexColor("#e8f4f8")
    C_GREEN  = colors.HexColor("#2d6a4f")
    C_RED    = colors.HexColor("#c1121f")
    C_GRACE  = colors.HexColor("#e9c46a")
    C_WHITE  = colors.white
    C_GREY   = colors.HexColor("#6c757d")

    def sty(name, **kw):
        return ParagraphStyle(name, **kw)

    st_title   = sty("title",   fontSize=22, textColor=C_WHITE,   alignment=TA_CENTER, fontName="Helvetica-Bold", spaceAfter=2)
    st_sub     = sty("sub",     fontSize=10, textColor=colors.HexColor("#a8dadc"), alignment=TA_CENTER, spaceAfter=0)
    st_label   = sty("label",   fontSize=9,  textColor=C_GREY,    fontName="Helvetica-Bold")
    st_value   = sty("value",   fontSize=9,  textColor=C_DARK)
    st_sechead = sty("sechead", fontSize=13, textColor=C_WHITE,   fontName="Helvetica-Bold", alignment=TA_CENTER)
    st_qtext   = sty("qtext",   fontSize=7.5,textColor=C_DARK,    leading=10)
    st_small   = sty("small",   fontSize=8,  textColor=C_DARK)
    st_centre  = sty("centre",  fontSize=8,  textColor=C_DARK,    alignment=TA_CENTER)
    st_total   = sty("total",   fontSize=11, textColor=C_DARK,    fontName="Helvetica-Bold", alignment=TA_CENTER)

    story = []
    W = A4[0] - 30*mm

    def banner(text, style, bg, h=12*mm, radius=3):
        tbl = Table([[Paragraph(text, style)]], colWidths=[W], rowHeights=[h])
        tbl.setStyle(TableStyle([
            ("BACKGROUND",  (0,0), (-1,-1), bg),
            ("ROUNDEDCORNERS", [radius]),
            ("VALIGN",      (0,0), (-1,-1), "MIDDLE"),
            ("LEFTPADDING",  (0,0), (-1,-1), 6),
            ("RIGHTPADDING", (0,0), (-1,-1), 6),
        ]))
        return tbl

    story.append(banner("COMPUTER BASED ASSESSMENT SYSTEM", st_title, C_DARK, h=18*mm))
    story.append(Spacer(1, 1*mm))
    story.append(banner("Report Card", st_sub, C_ACCENT, h=8*mm))
    story.append(Spacer(1, 5*mm))

    name    = student_info.get("name", "-")
    section = student_info.get("section", "-")
    reg     = student_info.get("registrationNumber", "-")
    start   = student_info.get("startTime", "-")
    end     = student_info.get("endTime", "-") or "-"
    gen     = datetime.now().strftime("%d %b %Y, %I:%M %p")

    detail_data = [
        [Paragraph("Name",                st_label), Paragraph(name,    st_value),
         Paragraph("Section",             st_label), Paragraph(section, st_value)],
        [Paragraph("Registration Number", st_label), Paragraph(reg,     st_value),
         Paragraph("Generated On",        st_label), Paragraph(gen,     st_value)],
        [Paragraph("Start Time",          st_label), Paragraph(start,   st_value),
         Paragraph("End Time",            st_label), Paragraph(end,     st_value)],
    ]
    cw = [28*mm, W/2 - 28*mm, 28*mm, W/2 - 28*mm]
    detail_tbl = Table(detail_data, colWidths=cw, rowHeights=[8*mm]*3)
    detail_tbl.setStyle(TableStyle([
        ("BACKGROUND",   (0,0), (-1,-1), C_LIGHT),
        ("BOX",          (0,0), (-1,-1), 0.5, C_BLUE),
        ("INNERGRID",    (0,0), (-1,-1), 0.3, colors.HexColor("#b0c4de")),
        ("VALIGN",       (0,0), (-1,-1), "MIDDLE"),
        ("LEFTPADDING",  (0,0), (-1,-1), 4),
        ("RIGHTPADDING", (0,0), (-1,-1), 4),
    ]))
    story.append(detail_tbl)
    story.append(Spacer(1, 6*mm))

    story.append(banner("PERFORMANCE SUMMARY", st_sechead, C_BLUE, h=9*mm))
    story.append(Spacer(1, 2*mm))

    total_marks  = sum(d["marks"] for d in all_data)
    total_out_of = sum(d["total"] for d in all_data)

    sum_header = ["Subject", "Total Qs", "Attempted", "Correct", "Wrong", "Skipped", "Grace", "Marks"]
    sum_rows   = [[
        d["subject"], d["total"], d["attempted"],
        d["correct"], d["wrong"], d["skipped"], d["grace"],
        f"{d['marks']} / {d['total']}"
    ] for d in all_data]
    sum_rows.append(["TOTAL", total_out_of,
                     sum(d["attempted"] for d in all_data),
                     sum(d["correct"]   for d in all_data),
                     sum(d["wrong"]     for d in all_data),
                     sum(d["skipped"]   for d in all_data),
                     sum(d["grace"]     for d in all_data),
                     f"{total_marks} / {total_out_of}"])

    sum_cw = [35*mm, 18*mm, 22*mm, 20*mm, 18*mm, 20*mm, 18*mm, 29*mm]
    sum_tbl_data = [[Paragraph(str(c), sty("sh", fontSize=8, textColor=C_WHITE,
                                           fontName="Helvetica-Bold", alignment=TA_CENTER))
                     for c in sum_header]]
    for i, row in enumerate(sum_rows):
        is_total = (i == len(sum_rows) - 1)
        fnt = "Helvetica-Bold" if is_total else "Helvetica"
        sum_tbl_data.append([
            Paragraph(str(c), sty(f"sc{i}", fontSize=8, fontName=fnt,
                                  textColor=C_DARK, alignment=TA_CENTER))
            for c in row
        ])

    sum_tbl = Table(sum_tbl_data, colWidths=sum_cw, repeatRows=1)
    ts = TableStyle([
        ("BACKGROUND",  (0, 0), (-1, 0),  C_BLUE),
        ("BACKGROUND",  (0, -1),(-1, -1), C_DARK),
        ("TEXTCOLOR",   (0, -1),(-1, -1), C_WHITE),
        ("ROWBACKGROUNDS", (0, 1), (-1, -2), [C_WHITE, C_LIGHT]),
        ("BOX",         (0, 0), (-1, -1), 0.5, C_BLUE),
        ("INNERGRID",   (0, 0), (-1, -1), 0.3, colors.HexColor("#b0c4de")),
        ("VALIGN",      (0, 0), (-1, -1), "MIDDLE"),
        ("ROWHEIGHT",   (0, 0), (-1, -1), 8*mm),
    ])
    sum_tbl.setStyle(ts)
    story.append(sum_tbl)
    story.append(Spacer(1, 5*mm))

    pct = round(total_marks / total_out_of * 100, 1) if total_out_of else 0
    badge_text = f"Total Marks: {total_marks} / {total_out_of}    ({pct}%)"
    story.append(banner(badge_text, st_total, C_LIGHT, h=12*mm))

    RESULT_BG = {
        "Correct":  colors.HexColor("#d4edda"),
        "Wrong":    colors.HexColor("#f8d7da"),
        "Grace +1": colors.HexColor("#fff3cd"),
        "Skipped":  colors.HexColor("#f1f3f4"),
    }
    RESULT_FG = {
        "Correct":  C_GREEN,
        "Wrong":    C_RED,
        "Grace +1": colors.HexColor("#b5651d"),
        "Skipped":  C_GREY,
    }

    for data in all_data:
        story.append(PageBreak())
        story.append(banner(data["subject"].upper(), st_sechead, C_DARK, h=11*mm))
        story.append(Spacer(1, 4*mm))

        info_data = [
            [Paragraph("Name",                st_label), Paragraph(name,    st_value),
             Paragraph("Section",             st_label), Paragraph(section, st_value)],
            [Paragraph("Registration Number", st_label), Paragraph(reg,     st_value),
             Paragraph("",                    st_label), Paragraph("",      st_value)],
        ]
        cw2 = [32*mm, W/2 - 32*mm, 32*mm, W/2 - 32*mm]
        info_tbl = Table(info_data, colWidths=cw2, rowHeights=[7*mm, 7*mm])
        info_tbl.setStyle(TableStyle([
            ("BACKGROUND",  (0, 0), (-1, -1), C_LIGHT),
            ("BOX",         (0, 0), (-1, -1), 0.5, C_BLUE),
            ("INNERGRID",   (0, 0), (-1, -1), 0.3, colors.HexColor("#b0c4de")),
            ("VALIGN",      (0, 0), (-1, -1), "MIDDLE"),
            ("LEFTPADDING", (0, 0), (-1, -1), 4),
        ]))
        story.append(info_tbl)
        story.append(Spacer(1, 4*mm))

        q_header = ["Q_ID", "Question", "Correct Option", "Student Option", "Result"]
        q_cw     = [26*mm, 76*mm, 28*mm, 28*mm, 22*mm]

        q_tbl_data = [[
            Paragraph(c, sty(f"qh{c}", fontSize=8, textColor=C_WHITE,
                             fontName="Helvetica-Bold", alignment=TA_CENTER))
            for c in q_header
        ]]

        for row in data["rows"]:
            fg = RESULT_FG.get(row["result"], C_DARK)
            q_tbl_data.append([
                Paragraph(str(row["qid"]),  sty("c1", fontSize=6.5, alignment=TA_CENTER, textColor=C_GREY)),
                Paragraph(row["question"],  sty("c2", fontSize=7.5, textColor=C_DARK, leading=10)),
                Paragraph(row["correct"],   sty("c3", fontSize=7.5, alignment=TA_CENTER, textColor=C_DARK)),
                Paragraph(row["student"],   sty("c4", fontSize=7.5, alignment=TA_CENTER, textColor=C_DARK)),
                Paragraph(row["result"],    sty("c5", fontSize=7.5, alignment=TA_CENTER,
                                               textColor=fg, fontName="Helvetica-Bold")),
            ])

        q_tbl = Table(q_tbl_data, colWidths=q_cw, repeatRows=1)
        q_ts  = TableStyle([
            ("BACKGROUND",     (0, 0), (-1, 0),  C_BLUE),
            ("ROWBACKGROUNDS", (0, 1), (-1, -1), [C_WHITE, C_LIGHT]),
            ("BOX",            (0, 0), (-1, -1), 0.5, C_BLUE),
            ("INNERGRID",      (0, 0), (-1, -1), 0.3, colors.HexColor("#b0c4de")),
            ("VALIGN",         (0, 0), (-1, -1), "MIDDLE"),
            ("TOPPADDING",     (0, 0), (-1, -1), 3),
            ("BOTTOMPADDING",  (0, 0), (-1, -1), 3),
        ])
        for i, row in enumerate(data["rows"], 1):
            bg = RESULT_BG.get(row["result"])
            if bg:
                q_ts.add("BACKGROUND", (4, i), (4, i), bg)
        q_tbl.setStyle(q_ts)
        story.append(q_tbl)
        story.append(Spacer(1, 4*mm))

        summary_cells = [
            f"Marks: {data['marks']} / {data['total']}",
            f"Attempted: {data['attempted']}",
            f"Correct: {data['correct']}",
            f"Wrong: {data['wrong']}",
            f"Skipped: {data['skipped']}",
            f"Grace: {data['grace']}",
        ]
        ncols   = len(summary_cells)
        scw     = [W / ncols] * ncols
        s_style = sty("sm", fontSize=8, fontName="Helvetica-Bold",
                      textColor=C_DARK, alignment=TA_CENTER)
        sum_row_tbl = Table(
            [[Paragraph(c, s_style) for c in summary_cells]],
            colWidths=scw, rowHeights=[9*mm]
        )
        sum_row_tbl.setStyle(TableStyle([
            ("BACKGROUND",  (0, 0), (0, 0),  colors.HexColor("#d4edda")),
            ("BACKGROUND",  (1, 0), (1, 0),  C_LIGHT),
            ("BACKGROUND",  (2, 0), (2, 0),  colors.HexColor("#d4edda")),
            ("BACKGROUND",  (3, 0), (3, 0),  colors.HexColor("#f8d7da")),
            ("BACKGROUND",  (4, 0), (4, 0),  C_LIGHT),
            ("BACKGROUND",  (5, 0), (5, 0),  colors.HexColor("#fff3cd")),
            ("BOX",         (0, 0), (-1, -1), 0.5, C_BLUE),
            ("INNERGRID",   (0, 0), (-1, -1), 0.3, colors.HexColor("#b0c4de")),
            ("VALIGN",      (0, 0), (-1, -1), "MIDDLE"),
        ]))
        story.append(sum_row_tbl)

    doc.build(story)
    print(f"\nPDF report card → {pdf_path}")
    return pdf_path


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
def main():
    print(f"CBT folder   : {CBT_DIR}")
    print(f"Evaluator dir: {EVAL_DIR}\n")

    # Step 1: Download solution.json from the URL in config.json
    print("=== Downloading solution ===")
    download_solution()

    # Step 2: Verify required files exist
    for label, path in [("responses.json",          RESPONSE_FILE),
                         ("solutions/solution.json", ANSWER_KEY_FILE)]:
        if not os.path.exists(path):
            print(f"ERROR: '{label}' not found at:\n  {path}")
            sys.exit(1)

    responses    = load_json(RESPONSE_FILE)
    answer_keys  = load_json(ANSWER_KEY_FILE)
    student_info = load_examinee()

    all_data     = []
    summary_rows = []

    # Step 3: Evaluate each subject
    print("\n=== Evaluating responses ===")
    for subject, student_answers in responses.items():
        if subject not in answer_keys:
            print(f"[WARN] '{subject}' not in solution.json - skipping")
            continue

        sol_path  = os.path.join(SOLUTIONS_DIR, f"{subject}.json")
        solutions = load_json(sol_path) if os.path.exists(sol_path) else {}

        data = evaluate_subject(subject, student_answers, answer_keys[subject], solutions)
        all_data.append(data)

        csv_path = write_csv(data, student_info)
        summary_rows.append([data["subject"], data["marks"], data["total"]])

    if not all_data:
        print("No reports generated.")
        return

    # Step 4: Summary CSV
    summary_path = os.path.join(REPORT_DIR, "summary.csv")
    total_marks  = sum(d["marks"] for d in all_data)
    total_out_of = sum(d["total"] for d in all_data)
    with open(summary_path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["Name",    student_info.get("name", "")])
        w.writerow(["Section", student_info.get("section", "")])
        w.writerow(["Reg No",  student_info.get("registrationNumber", "")])
        w.writerow([])
        w.writerow(["Subject", "Marks Obtained", "Total Questions"])
        w.writerows(summary_rows)
        w.writerow([])
        w.writerow(["TOTAL", total_marks, total_out_of])

    print(f"Summary CSV  → {summary_path}")
    print(f"Overall      : {total_marks} / {total_out_of}")

    # Step 5: Generate PDF
    print("\n=== Generating PDF ===")
    pdf_path = write_pdf(all_data, student_info)

    # Step 6: Upload to Dropbox — cleanup only if upload succeeds
    print("\n=== Uploading to Dropbox ===")
    upload_ok = upload_to_dropbox(pdf_path, os.path.basename(pdf_path))

    if upload_ok:
        print("\n=== Dropbox upload confirmed — cleaning up ===")
        cleanup_cbt_folder()
    else:
        print("\n[WARNING] Dropbox upload failed — skipping cleanup to avoid data loss.")
        print("          Fix the upload issue and re-run, or manually delete files.")

    # Step 7: Open PDF
    if os.path.exists(pdf_path):
        os.startfile(pdf_path)


if __name__ == "__main__":
    main()