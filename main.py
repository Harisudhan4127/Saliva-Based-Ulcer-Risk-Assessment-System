import sys, os, uuid, sqlite3
import joblib
import pandas as pd
from datetime import datetime

from PyQt5.QtWidgets import *
from PyQt5.QtGui import QIcon, QFont, QColor, QPalette, QPixmap, QPainter, QBrush, QPen, QFontDatabase
from PyQt5.QtCore import Qt, QThread, pyqtSignal, QPropertyAnimation, QEasingCurve, QTimer

# ── Updater ────────────────────────────────────────────────────────────────────
from updater import UpdateWorker, launch_installer, CURRENT_VERSION

try:
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
    from reportlab.lib.units import mm
    from reportlab.lib import colors
    from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, HRFlowable
    from reportlab.lib.enums import TA_CENTER, TA_LEFT, TA_RIGHT
    REPORTLAB_AVAILABLE = True
except ImportError:
    REPORTLAB_AVAILABLE = False

# ─── PATHS ────────────────────────────────────────────────────────────────────
if getattr(sys, 'frozen', False):
    BASE_DIR = sys._MEIPASS
else:
    BASE_DIR = os.path.dirname(os.path.abspath(__file__))

model_path    = os.path.join(BASE_DIR, "src", "data.pkl")
user_data_dir = os.path.join(BASE_DIR, "User_data")
os.makedirs(user_data_dir, exist_ok=True)
DB_PATH = os.path.join(user_data_dir, "patients.db")

feature_names = [
    "Age", "Gender", "Spicy_Food", "Tobacco", "Alcohol",
    "Sleep_Hours", "Stress", "Skip_Meals",
    "Soft_Drinks", "Empty_Stomach_Pain",
    "Day1_pH_Before", "Day1_pH_After",
    "Day2_pH_Before", "Day2_pH_After"
]

# ─── LOAD MODEL ───────────────────────────────────────────────────────────────
try:
    model = joblib.load(model_path)
except Exception:
    class _MockModel:
        def predict_proba(self, df):
            import random
            p = random.random()
            return [[1 - p, p]]
    model = _MockModel()

# ─── DATABASE ─────────────────────────────────────────────────────────────────
def init_db():
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    c.execute("""
        CREATE TABLE IF NOT EXISTS patients (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            patient_id  TEXT UNIQUE,
            name        TEXT,
            age         REAL,
            gender      TEXT,
            spicy_food  INTEGER,
            tobacco     INTEGER,
            alcohol     INTEGER,
            sleep_hours REAL,
            stress      REAL,
            skip_meals  INTEGER,
            soft_drinks INTEGER,
            empty_stomach_pain INTEGER,
            day1_ph_before REAL,
            day1_ph_after  REAL,
            day2_ph_before REAL,
            day2_ph_after  REAL,
            risk_level  TEXT,
            probability REAL,
            created_at  TEXT
        )
    """)
    conn.commit()
    conn.close()

def save_patient(data: dict):
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    c.execute("""
        INSERT OR REPLACE INTO patients (
            patient_id, name, age, gender, spicy_food, tobacco, alcohol,
            sleep_hours, stress, skip_meals, soft_drinks, empty_stomach_pain,
            day1_ph_before, day1_ph_after, day2_ph_before, day2_ph_after,
            risk_level, probability, created_at
        ) VALUES (
            :patient_id, :name, :age, :gender, :spicy_food, :tobacco, :alcohol,
            :sleep_hours, :stress, :skip_meals, :soft_drinks, :empty_stomach_pain,
            :day1_ph_before, :day1_ph_after, :day2_ph_before, :day2_ph_after,
            :risk_level, :probability, :created_at
        )
    """, data)
    conn.commit()
    conn.close()

def load_all_patients():
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    c.execute("SELECT patient_id, name, risk_level, probability, created_at FROM patients ORDER BY id DESC")
    rows = c.fetchall()
    conn.close()
    return rows

def load_patient(patient_id):
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    c = conn.cursor()
    c.execute("SELECT * FROM patients WHERE patient_id=?", (patient_id,))
    row = c.fetchone()
    conn.close()
    return dict(row) if row else None

def delete_patient(patient_id):
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    c.execute("DELETE FROM patients WHERE patient_id=?", (patient_id,))
    conn.commit()
    conn.close()

init_db()

# ─── PDF EXPORT ───────────────────────────────────────────────────────────────
def export_pdf(data: dict, save_path: str):
    if not REPORTLAB_AVAILABLE:
        raise RuntimeError("reportlab is not installed. Run: pip install reportlab")

    doc = SimpleDocTemplate(
        save_path, pagesize=A4,
        rightMargin=20*mm, leftMargin=20*mm,
        topMargin=20*mm, bottomMargin=20*mm
    )
    W, H = A4
    story = []
    styles = getSampleStyleSheet()

    TEAL   = colors.HexColor("#0f766e")
    SLATE  = colors.HexColor("#1e293b")
    LTGRAY = colors.HexColor("#f1f5f9")
    RED    = colors.HexColor("#dc2626")
    AMBER  = colors.HexColor("#d97706")
    GREEN  = colors.HexColor("#16a34a")
    risk_color = {"HIGH": RED, "MEDIUM": AMBER, "LOW": GREEN}.get(data.get("risk_level","LOW"), GREEN)

    header_style = ParagraphStyle("header", fontSize=20, textColor=colors.white,
                                  fontName="Helvetica-Bold", alignment=TA_LEFT, spaceAfter=2)
    sub_style    = ParagraphStyle("sub",    fontSize=9,  textColor=colors.HexColor("#94a3b8"),
                                  fontName="Helvetica",    alignment=TA_LEFT)
    sec_style    = ParagraphStyle("sec",    fontSize=11, textColor=SLATE,
                                  fontName="Helvetica-Bold", spaceBefore=8, spaceAfter=4)
    body_style   = ParagraphStyle("body",   fontSize=9,  textColor=SLATE,
                                  fontName="Helvetica",    leading=14)

    header_table = Table(
        [[Paragraph("ULCER RISK ASSESSMENT REPORT", header_style),
          Paragraph(f"Version {CURRENT_VERSION}", sub_style)]],
        colWidths=[(W-40*mm)*0.72, (W-40*mm)*0.28]
    )
    header_table.setStyle(TableStyle([
        ("BACKGROUND",    (0,0),(-1,-1), TEAL),
        ("VALIGN",        (0,0),(-1,-1), "MIDDLE"),
        ("TOPPADDING",    (0,0),(-1,-1), 14),
        ("BOTTOMPADDING", (0,0),(-1,-1), 14),
        ("LEFTPADDING",   (0,0),(0,-1),  12),
        ("RIGHTPADDING",  (-1,0),(-1,-1),12),
        ("ALIGN",         (1,0),(1,-1),  "RIGHT"),
    ]))
    story.append(header_table)
    story.append(Spacer(1, 6*mm))

    pid   = data.get("patient_id","—")
    name  = data.get("name","—")
    cdate = data.get("created_at","—")[:16] if data.get("created_at") else "—"
    prob  = data.get("probability", 0)
    risk  = data.get("risk_level","—")

    info_rows = [
        [Paragraph("<b>Patient ID</b>", body_style), Paragraph(pid,   body_style),
         Paragraph("<b>Name</b>",       body_style), Paragraph(name,  body_style)],
        [Paragraph("<b>Date</b>",       body_style), Paragraph(cdate, body_style),
         Paragraph("<b>Gender</b>",     body_style), Paragraph(str(data.get("gender","—")), body_style)],
    ]
    info_table = Table(info_rows, colWidths=[(W-40*mm)/4]*4)
    info_table.setStyle(TableStyle([
        ("BACKGROUND",    (0,0),(-1,-1), LTGRAY),
        ("TOPPADDING",    (0,0),(-1,-1), 6),
        ("BOTTOMPADDING", (0,0),(-1,-1), 6),
        ("LEFTPADDING",   (0,0),(-1,-1), 8),
        ("GRID",          (0,0),(-1,-1), 0.3, colors.HexColor("#cbd5e1")),
    ]))
    story.append(info_table)
    story.append(Spacer(1, 5*mm))

    pct = f"{prob*100:.1f}%"
    risk_row = [[
        Paragraph(f"<b>RISK LEVEL: {risk}</b>", ParagraphStyle(
            "risk", fontSize=15, textColor=colors.white, fontName="Helvetica-Bold", alignment=TA_LEFT)),
        Paragraph(f"<b>Probability: {pct}</b>", ParagraphStyle(
            "prob", fontSize=12, textColor=colors.white, fontName="Helvetica", alignment=TA_RIGHT))
    ]]
    risk_table = Table(risk_row, colWidths=[(W-40*mm)*0.6, (W-40*mm)*0.4])
    risk_table.setStyle(TableStyle([
        ("BACKGROUND",    (0,0),(-1,-1), risk_color),
        ("TOPPADDING",    (0,0),(-1,-1), 12),
        ("BOTTOMPADDING", (0,0),(-1,-1), 12),
        ("LEFTPADDING",   (0,0),(0,-1),  12),
        ("RIGHTPADDING",  (-1,0),(-1,-1),12),
        ("VALIGN",        (0,0),(-1,-1), "MIDDLE"),
    ]))
    story.append(risk_table)
    story.append(Spacer(1, 6*mm))

    story.append(Paragraph("LIFESTYLE &amp; DIETARY FACTORS", sec_style))
    story.append(HRFlowable(width="100%", thickness=1, color=TEAL, spaceAfter=4))
    yn = lambda v: "Yes" if v == 1 else "No"
    lifestyle_rows = [
        ["Factor","Value","Factor","Value"],
        ["Age", str(data.get("age","—")), "Sleep Hours/Day", str(data.get("sleep_hours","—"))],
        ["Spicy Food", yn(data.get("spicy_food",0)), "Stress Level (1-10)", str(data.get("stress","—"))],
        ["Tobacco Use", yn(data.get("tobacco",0)), "Skip Meals", yn(data.get("skip_meals",0))],
        ["Alcohol Use", yn(data.get("alcohol",0)), "Soft Drinks", yn(data.get("soft_drinks",0))],
        ["Empty Stomach Pain", yn(data.get("empty_stomach_pain",0)), "", ""],
    ]
    lf_table = Table(lifestyle_rows, colWidths=[(W-40*mm)/4]*4)
    lf_table.setStyle(TableStyle([
        ("BACKGROUND",    (0,0),(-1,0),  SLATE),
        ("TEXTCOLOR",     (0,0),(-1,0),  colors.white),
        ("FONTNAME",      (0,0),(-1,0),  "Helvetica-Bold"),
        ("FONTSIZE",      (0,0),(-1,-1), 8.5),
        ("ROWBACKGROUNDS",(0,1),(-1,-1), [colors.white, LTGRAY]),
        ("TOPPADDING",    (0,0),(-1,-1), 5),
        ("BOTTOMPADDING", (0,0),(-1,-1), 5),
        ("LEFTPADDING",   (0,0),(-1,-1), 8),
        ("GRID",          (0,0),(-1,-1), 0.3, colors.HexColor("#cbd5e1")),
    ]))
    story.append(lf_table)
    story.append(Spacer(1, 5*mm))

    story.append(Paragraph("GASTRIC pH READINGS", sec_style))
    story.append(HRFlowable(width="100%", thickness=1, color=TEAL, spaceAfter=4))
    ph_rows = [
        ["Measurement","pH Value","Measurement","pH Value"],
        ["Day 1 — Before", str(data.get("day1_ph_before","—")),
         "Day 1 — After",  str(data.get("day1_ph_after","—"))],
        ["Day 2 — Before", str(data.get("day2_ph_before","—")),
         "Day 2 — After",  str(data.get("day2_ph_after","—"))],
    ]
    ph_table = Table(ph_rows, colWidths=[(W-40*mm)/4]*4)
    ph_table.setStyle(TableStyle([
        ("BACKGROUND",    (0,0),(-1,0),  SLATE),
        ("TEXTCOLOR",     (0,0),(-1,0),  colors.white),
        ("FONTNAME",      (0,0),(-1,0),  "Helvetica-Bold"),
        ("FONTSIZE",      (0,0),(-1,-1), 8.5),
        ("ROWBACKGROUNDS",(0,1),(-1,-1), [colors.white, LTGRAY]),
        ("TOPPADDING",    (0,0),(-1,-1), 5),
        ("BOTTOMPADDING", (0,0),(-1,-1), 5),
        ("LEFTPADDING",   (0,0),(-1,-1), 8),
        ("GRID",          (0,0),(-1,-1), 0.3, colors.HexColor("#cbd5e1")),
    ]))
    story.append(ph_table)
    story.append(Spacer(1, 5*mm))

    story.append(Paragraph("CLINICAL INTERPRETATION", sec_style))
    story.append(HRFlowable(width="100%", thickness=1, color=TEAL, spaceAfter=4))
    interp = {
        "HIGH":   "Patient shows a HIGH probability of peptic ulcer risk. Immediate clinical evaluation is strongly recommended. Consider endoscopy, H. pylori testing, and PPI therapy.",
        "MEDIUM": "Patient shows MODERATE risk indicators. Lifestyle modifications are advised. Follow-up assessment in 4–6 weeks is recommended.",
        "LOW":    "Patient shows LOW risk indicators at this time. Continue routine monitoring and maintain healthy dietary habits.",
    }.get(risk, "—")
    story.append(Paragraph(interp, body_style))
    story.append(Spacer(1, 8*mm))

    story.append(HRFlowable(width="100%", thickness=0.5, color=colors.HexColor("#cbd5e1")))
    story.append(Spacer(1, 3*mm))
    footer_style = ParagraphStyle("footer", fontSize=7.5, textColor=colors.HexColor("#94a3b8"),
                                  fontName="Helvetica", alignment=TA_CENTER)
    story.append(Paragraph(
        f"Generated by Ulcer Risk AI {CURRENT_VERSION} · {datetime.now().strftime('%d %b %Y %H:%M')} · "
        "This report is for clinical reference only and does not replace professional medical advice.",
        footer_style
    ))
    doc.build(story)

# ═══════════════════════════════════════════════════════════════════════════════
# STYLESHEET
# ═══════════════════════════════════════════════════════════════════════════════
APP_STYLE = """
QWidget {
    background: #f8fafc;
    color: #1e293b;
    font-family: 'Segoe UI', 'SF Pro Display', Helvetica, sans-serif;
    font-size: 13px;
}
QScrollBar:vertical {
    background: #f1f5f9; width: 8px; border-radius: 4px;
}
QScrollBar::handle:vertical {
    background: #cbd5e1; border-radius: 4px; min-height: 30px;
}
#sidebar {
    background: #0f172a;
    border-right: 1px solid #1e293b;
}
#sidebar QLabel { color: #94a3b8; }
#sidebar QPushButton {
    background: transparent;
    color: #94a3b8;
    border: none;
    text-align: left;
    padding: 10px 16px;
    border-radius: 6px;
    font-size: 13px;
}
#sidebar QPushButton:hover  { background: #1e293b; color: #e2e8f0; }
#sidebar QPushButton:checked { background: #0f766e; color: white; font-weight: 600; }
#topbar { background: white; border-bottom: 1px solid #e2e8f0; }
#card { background: white; border: 1px solid #e2e8f0; border-radius: 10px; }
#secHeader { color: #0f766e; font-size: 11px; font-weight: 700; letter-spacing: 1px; }
QLineEdit, QComboBox, QSpinBox, QDoubleSpinBox {
    background: #f8fafc;
    border: 1.5px solid #e2e8f0;
    border-radius: 7px;
    padding: 8px 12px;
    color: #1e293b;
    font-size: 13px;
    selection-background-color: #0f766e;
}
QLineEdit:focus, QComboBox:focus { border-color: #0f766e; background: white; }
QComboBox::drop-down { border: none; width: 28px; }
QComboBox::down-arrow {
    image: none;
    border-left: 5px solid transparent;
    border-right: 5px solid transparent;
    border-top: 5px solid #64748b;
    width: 0; height: 0; margin-right: 8px;
}
QComboBox QAbstractItemView {
    background: white; border: 1px solid #e2e8f0;
    selection-background-color: #ccfbf1; selection-color: #0f766e; padding: 4px;
}
#btnPrimary {
    background: #0f766e; color: white; border: none;
    border-radius: 8px; padding: 11px 28px; font-size: 14px; font-weight: 600;
}
#btnPrimary:hover   { background: #0d6460; }
#btnPrimary:pressed { background: #0a4f4b; }
#btnSecondary {
    background: white; color: #0f766e; border: 1.5px solid #0f766e;
    border-radius: 8px; padding: 9px 22px; font-size: 13px; font-weight: 600;
}
#btnSecondary:hover   { background: #f0fdf4; }
#btnSecondary:pressed { background: #ccfbf1; }
#btnDanger {
    background: white; color: #dc2626; border: 1.5px solid #dc2626;
    border-radius: 8px; padding: 9px 22px; font-size: 13px; font-weight: 600;
}
#btnDanger:hover { background: #fef2f2; }
QListWidget { background: transparent; border: none; outline: none; }
QListWidget::item {
    background: white; border: 1px solid #e2e8f0; border-radius: 8px;
    padding: 10px 12px; margin: 3px 0; color: #1e293b;
}
QListWidget::item:hover    { background: #f0fdf4; border-color: #6ee7b7; }
QListWidget::item:selected { background: #ccfbf1; border-color: #0f766e; color: #065f46; }
#riskHigh   { background:#fef2f2; color:#dc2626; border:1.5px solid #fca5a5; border-radius:12px; padding:4px 14px; font-weight:700; font-size:12px; }
#riskMedium { background:#fffbeb; color:#d97706; border:1.5px solid #fcd34d; border-radius:12px; padding:4px 14px; font-weight:700; font-size:12px; }
#riskLow    { background:#f0fdf4; color:#16a34a; border:1.5px solid #86efac; border-radius:12px; padding:4px 14px; font-weight:700; font-size:12px; }
#resultHigh   { background:#fef2f2; border:2px solid #fca5a5; border-radius:12px; padding:18px; }
#resultMedium { background:#fffbeb; border:2px solid #fcd34d; border-radius:12px; padding:18px; }
#resultLow    { background:#f0fdf4; border:2px solid #86efac; border-radius:12px; padding:18px; }
QProgressBar {
    background: #e2e8f0; border-radius: 4px; height: 8px; border: none; text-align: center;
}
QProgressBar::chunk { background: #0f766e; border-radius: 4px; }
QTabBar::tab { background: transparent; color: #64748b; padding: 10px 20px; border-bottom: 2px solid transparent; font-size: 13px; }
QTabBar::tab:selected { color: #0f766e; border-bottom-color: #0f766e; font-weight: 600; }
QTabBar::tab:hover    { color: #1e293b; }
QTabWidget::pane      { border: none; }
QTableWidget { background: white; border: 1px solid #e2e8f0; border-radius: 8px; gridline-color: #f1f5f9; }
QTableWidget::item { padding: 8px 12px; }
QTableWidget::item:selected { background: #ccfbf1; color: #065f46; }
QHeaderView::section {
    background: #f8fafc; color: #64748b; font-size: 11px; font-weight: 700;
    letter-spacing: 0.5px; padding: 10px 12px; border: none; border-bottom: 1px solid #e2e8f0;
}

/* ── Update banner ── */
#updateBanner {
    background: #0f172a;
    border-bottom: 1px solid #1e293b;
}
#updateBanner QLabel { color: #94a3b8; font-size: 12px; background: transparent; }
#updateProgressBar {
    background: #1e293b; border-radius: 3px; height: 6px; border: none; text-align: center;
}
#updateProgressBar::chunk { background: #0f766e; border-radius: 3px; }
#btnUpdate {
    background: #0f766e; color: white; border: none;
    border-radius: 6px; padding: 6px 16px; font-size: 12px; font-weight: 600;
}
#btnUpdate:hover { background: #0d6460; }
#btnUpdateDismiss {
    background: transparent; color: #64748b; border: 1px solid #334155;
    border-radius: 6px; padding: 5px 12px; font-size: 12px;
}
#btnUpdateDismiss:hover { color: #94a3b8; border-color: #475569; }
"""

# ═══════════════════════════════════════════════════════════════════════════════
# UPDATE BANNER WIDGET
# ═══════════════════════════════════════════════════════════════════════════════
class UpdateBanner(QWidget):
    """
    Slim top banner that shows update state:
      idle → checking → downloading (progress) → ready to install → up-to-date / error
    """
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setObjectName("updateBanner")
        self.setFixedHeight(44)
        self._worker = None
        self._install_path = None
        self._build()
        self.hide()   # hidden until we have something to show

    def _build(self):
        lay = QHBoxLayout(self)
        lay.setContentsMargins(16, 0, 12, 0)
        lay.setSpacing(12)

        # status icon (spinning dots simulation via timer)
        self._icon = QLabel("⟳")
        self._icon.setFixedWidth(20)
        self._icon.setStyleSheet("color:#0f766e; font-size:16px; background:transparent;")
        lay.addWidget(self._icon)

        # status text
        self._lbl = QLabel("Checking for updates…")
        lay.addWidget(self._lbl)

        # progress bar (hidden when not downloading)
        self._bar = QProgressBar()
        self._bar.setObjectName("updateProgressBar")
        self._bar.setFixedHeight(6)
        self._bar.setFixedWidth(180)
        self._bar.setTextVisible(False)
        self._bar.setRange(0, 100)
        self._bar.setValue(0)
        self._bar.hide()
        lay.addWidget(self._bar)

        lay.addStretch()

        # "Install now" button (hidden until ready)
        self._btn_install = QPushButton("Install Now")
        self._btn_install.setObjectName("btnUpdate")
        self._btn_install.setFixedHeight(30)
        self._btn_install.setCursor(Qt.PointingHandCursor)
        self._btn_install.hide()
        self._btn_install.clicked.connect(self._do_install)
        lay.addWidget(self._btn_install)

        # dismiss button
        self._btn_dismiss = QPushButton("✕")
        self._btn_dismiss.setObjectName("btnUpdateDismiss")
        self._btn_dismiss.setFixedSize(28, 28)
        self._btn_dismiss.setCursor(Qt.PointingHandCursor)
        self._btn_dismiss.clicked.connect(self.hide)
        lay.addWidget(self._btn_dismiss)

        # Spinner animation timer
        self._spin_chars = ["⟳", "↻", "↺", "⟲"]
        self._spin_idx   = 0
        self._spin_timer = QTimer(self)
        self._spin_timer.timeout.connect(self._spin)
        self._spin_timer.setInterval(300)

    def _spin(self):
        self._spin_idx = (self._spin_idx + 1) % len(self._spin_chars)
        self._icon.setText(self._spin_chars[self._spin_idx])

    def start_check(self):
        """Launch the background worker."""
        self.show()
        self._bar.setValue(0)
        self._bar.hide()
        self._btn_install.hide()
        self._icon.setText("⟳")
        self._icon.setStyleSheet("color:#0f766e; font-size:16px; background:transparent;")
        self._lbl.setText("Checking for updates…")
        self._spin_timer.start()

        self._worker = UpdateWorker()
        self._worker.status_changed.connect(self._on_status)
        self._worker.progress_changed.connect(self._on_progress)
        self._worker.update_available.connect(self._on_update_available)
        self._worker.up_to_date.connect(self._on_up_to_date)
        self._worker.error_occurred.connect(self._on_error)
        self._worker.ready_to_install.connect(self._on_ready)
        self._worker.start()

    # ── slots ─────────────────────────────────────────────────────────────────
    def _on_status(self, msg):
        self._lbl.setText(msg)

    def _on_progress(self, pct):
        if not self._bar.isVisible():
            self._bar.show()
        self._bar.setValue(pct)

    def _on_update_available(self, version):
        self._icon.setStyleSheet("color:#d97706; font-size:16px; background:transparent;")
        self._icon.setText("↓")
        self._spin_timer.stop()

    def _on_up_to_date(self):
        self._spin_timer.stop()
        self._icon.setText("✓")
        self._icon.setStyleSheet("color:#16a34a; font-size:16px; background:transparent;")
        self._bar.hide()
        # auto-hide after 4 s
        QTimer.singleShot(4000, self.hide)

    def _on_error(self, msg):
        self._spin_timer.stop()
        self._icon.setText("✕")
        self._icon.setStyleSheet("color:#dc2626; font-size:16px; background:transparent;")
        self._bar.hide()
        QTimer.singleShot(5000, self.hide)

    def _on_ready(self, path):
        self._install_path = path
        self._spin_timer.stop()
        self._bar.hide()
        self._icon.setText("✓")
        self._icon.setStyleSheet("color:#16a34a; font-size:16px; background:transparent;")
        self._btn_install.show()

    def _do_install(self):
        if self._install_path:
            reply = QMessageBox.question(
                self, "Install Update",
                "The application will close and the installer will launch.\nContinue?",
                QMessageBox.Yes | QMessageBox.No
            )
            if reply == QMessageBox.Yes:
                launch_installer(self._install_path)


# ═══════════════════════════════════════════════════════════════════════════════
# LOGIN
# ═══════════════════════════════════════════════════════════════════════════════
class LoginPage(QWidget):
    def __init__(self, on_login):
        super().__init__()
        self.on_login = on_login
        self._build()

    def _build(self):
        outer = QVBoxLayout(self)
        outer.setAlignment(Qt.AlignCenter)

        card = QFrame()
        card.setFixedWidth(380)
        card.setStyleSheet("""
            QFrame { background: white; border: 1px solid #e2e8f0; border-radius: 16px; }
        """)
        cl = QVBoxLayout(card)
        cl.setContentsMargins(36, 40, 36, 40)
        cl.setSpacing(16)

        logo = QLabel("⬡")
        logo.setAlignment(Qt.AlignCenter)
        logo.setStyleSheet("font-size:42px; color:#0f766e; background:transparent; border:none;")
        cl.addWidget(logo)

        title = QLabel("Ulcer Risk AI")
        title.setAlignment(Qt.AlignCenter)
        title.setStyleSheet("font-size:22px; font-weight:700; color:#0f172a; background:transparent; border:none;")
        cl.addWidget(title)

        sub = QLabel("Clinical Assessment System")
        sub.setAlignment(Qt.AlignCenter)
        sub.setStyleSheet("font-size:13px; color:#64748b; margin-bottom:12px; background:transparent; border:none;")
        cl.addWidget(sub)

        self.user_input = QLineEdit()
        self.user_input.setPlaceholderText("Username")
        self.user_input.setFixedHeight(44)
        cl.addWidget(self.user_input)

        self.pwd_input = QLineEdit()
        self.pwd_input.setPlaceholderText("Password")
        self.pwd_input.setEchoMode(QLineEdit.Password)
        self.pwd_input.setFixedHeight(44)
        self.pwd_input.returnPressed.connect(self._login)
        cl.addWidget(self.pwd_input)

        btn = QPushButton("Sign In")
        btn.setObjectName("btnPrimary")
        btn.setFixedHeight(44)
        btn.setCursor(Qt.PointingHandCursor)
        btn.setStyleSheet("""
    QPushButton#btnPrimary {
        background-color: black;
        color: #888888;  /* Change text to gray */
        border: 1px solid #333;
        border-radius: 6px;
    }
    QPushButton#btnPrimary:hover {
        background-color: #222222;
        color: #bbbbbb;  /* Lighten text on hover */
    }
""")

        btn.clicked.connect(self._login)
        cl.addWidget(btn)

        self.err = QLabel("")
        self.err.setAlignment(Qt.AlignCenter)
        self.err.setStyleSheet("color:#dc2626; font-size:12px; background:transparent; border:none;")
        cl.addWidget(self.err)

        outer.addWidget(card, alignment=Qt.AlignCenter)

        v = QLabel(f"Version {CURRENT_VERSION.lstrip("v")}")
        v.setAlignment(Qt.AlignCenter)
        v.setStyleSheet("color:#94a3b8; font-size:11px;")
        outer.addWidget(v)

    def _login(self):
        if self.user_input.text() == "admin" and self.pwd_input.text() == "admin@123":
            self.on_login()
        else:
            self.err.setText("Invalid credentials. Please try again.")
            self.pwd_input.clear()


# ═══════════════════════════════════════════════════════════════════════════════
# FIELD BUILDER
# ═══════════════════════════════════════════════════════════════════════════════
COMBO_OPTIONS = {
    "Gender":              ["Male", "Female"],
    "Spicy_Food":          ["Yes", "No"],
    "Tobacco":             ["Yes", "No"],
    "Alcohol":             ["Yes", "No"],
    "Skip_Meals":          ["Yes", "No"],
    "Soft_Drinks":         ["Yes", "No"],
    "Empty_Stomach_Pain":  ["Yes", "No"],
}

LABELS = {
    "Age":                 "Age (years)",
    "Gender":              "Biological Sex",
    "Spicy_Food":          "Spicy Food Intake",
    "Tobacco":             "Tobacco Use",
    "Alcohol":             "Alcohol Consumption",
    "Sleep_Hours":         "Avg. Sleep (hrs/day)",
    "Stress":              "Stress Level (1–10)",
    "Skip_Meals":          "Skips Meals Regularly",
    "Soft_Drinks":         "Soft Drink Consumption",
    "Empty_Stomach_Pain":  "Pain on Empty Stomach",
    "Day1_pH_Before":      "Day 1 pH — Before Meal",
    "Day1_pH_After":       "Day 1 pH — After Meal",
    "Day2_pH_Before":      "Day 2 pH — Before Meal",
    "Day2_pH_After":       "Day 2 pH — After Meal",
}

def make_field(name):
    if name in COMBO_OPTIONS:
        w = QComboBox()
        w.addItems(COMBO_OPTIONS[name])
    else:
        w = QLineEdit()
        w.setPlaceholderText("Enter value")
    w.setFixedHeight(38)
    return w

def field_value(name, widget):
    if isinstance(widget, QComboBox):
        return 1 if widget.currentText() in ["Male", "Yes"] else 0
    else:
        return float(widget.text())

def set_field(name, widget, value):
    if isinstance(widget, QComboBox):
        widget.setCurrentIndex(0 if value == 1 else 1)
    else:
        widget.setText(str(value))


# ═══════════════════════════════════════════════════════════════════════════════
# NEW PATIENT FORM
# ═══════════════════════════════════════════════════════════════════════════════
class NewPatientPage(QWidget):
    patient_saved = pyqtSignal(str)

    def __init__(self):
        super().__init__()
        self.inputs = {}
        self._current_id  = None
        self._result_data = None
        self._build()

    def reset_for_new(self):
        self._current_id  = "PAT-" + str(uuid.uuid4())[:8].upper()
        self._result_data = None
        self.pid_val.setText(self._current_id)
        self.name_input.clear()
        for name, w in self.inputs.items():
            if isinstance(w, QComboBox):
                w.setCurrentIndex(0)
            else:
                w.clear()
        self.result_frame.hide()
        self.btn_save.setEnabled(False)
        self.btn_pdf.setEnabled(False)

    def load_patient(self, pid):
        data = load_patient(pid)
        if not data:
            return
        self._current_id  = pid
        self._result_data = data
        self.pid_val.setText(pid)
        self.name_input.setText(data.get("name",""))
        mapping = {
            "Age":"age","Gender":"gender","Spicy_Food":"spicy_food",
            "Tobacco":"tobacco","Alcohol":"alcohol","Sleep_Hours":"sleep_hours",
            "Stress":"stress","Skip_Meals":"skip_meals","Soft_Drinks":"soft_drinks",
            "Empty_Stomach_Pain":"empty_stomach_pain",
            "Day1_pH_Before":"day1_ph_before","Day1_pH_After":"day1_ph_after",
            "Day2_pH_Before":"day2_ph_before","Day2_pH_After":"day2_ph_after",
        }
        for fname, dkey in mapping.items():
            set_field(fname, self.inputs[fname], data.get(dkey, 0))
        self._show_result(data["risk_level"], data["probability"])
        self.btn_save.setEnabled(True)
        self.btn_pdf.setEnabled(True)

    def _build(self):
        root = QVBoxLayout(self)
        root.setContentsMargins(0,0,0,0)
        root.setSpacing(0)

        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.NoFrame)
        scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarAlwaysOff)
        inner = QWidget()
        vlay  = QVBoxLayout(inner)
        vlay.setContentsMargins(28,24,28,28)
        vlay.setSpacing(20)
        scroll.setWidget(inner)
        root.addWidget(scroll)

        ttl = QLabel("New Patient Assessment")
        ttl.setStyleSheet("font-size:20px; font-weight:700; color:#0f172a;")
        vlay.addWidget(ttl)

        # Patient ID strip
        id_card = QFrame(); id_card.setObjectName("card")
        id_lay  = QHBoxLayout(id_card)
        id_lay.setContentsMargins(16,12,16,12)
        id_lay.addWidget(QLabel("Patient ID"))
        self.pid_val = QLabel("—")
        self.pid_val.setStyleSheet("font-weight:700; color:#0f766e; font-size:14px;")
        id_lay.addWidget(self.pid_val)
        id_lay.addSpacing(24)
        id_lay.addWidget(QLabel("Patient Name"))
        self.name_input = QLineEdit()
        self.name_input.setPlaceholderText("Full name")
        self.name_input.setFixedHeight(36)
        self.name_input.setFixedWidth(200)
        id_lay.addWidget(self.name_input)
        id_lay.addStretch()
        ts = QLabel(f"Date: {datetime.now().strftime('%d %b %Y')}")
        ts.setStyleSheet("color:#64748b; font-size:12px;")
        id_lay.addWidget(ts)
        vlay.addWidget(id_card)

        sections = [
            ("PATIENT DEMOGRAPHICS",        ["Age", "Gender"]),
            ("LIFESTYLE & DIETARY HABITS",  ["Spicy_Food","Tobacco","Alcohol","Skip_Meals","Soft_Drinks"]),
            ("SLEEP & STRESS",              ["Sleep_Hours","Stress","Empty_Stomach_Pain"]),
            ("GASTRIC pH READINGS — DAY 1", ["Day1_pH_Before","Day1_pH_After"]),
            ("GASTRIC pH READINGS — DAY 2", ["Day2_pH_Before","Day2_pH_After"]),
        ]

        for sec_title, fields in sections:
            card = QFrame(); card.setObjectName("card")
            cl   = QVBoxLayout(card)
            cl.setContentsMargins(18,16,18,16)
            cl.setSpacing(12)
            sh = QLabel(sec_title); sh.setObjectName("secHeader")
            cl.addWidget(sh)
            grid = QGridLayout()
            grid.setHorizontalSpacing(16)
            grid.setVerticalSpacing(10)
            for i, fname in enumerate(fields):
                lbl = QLabel(LABELS[fname])
                lbl.setStyleSheet("color:#475569; font-size:12px; font-weight:500;")
                w = make_field(fname)
                self.inputs[fname] = w
                row, col = divmod(i, 2)
                grid.addWidget(lbl, row*2,   col*2)
                grid.addWidget(w,   row*2+1, col*2)
            grid.setColumnStretch(1, 1)
            grid.setColumnStretch(3, 1)
            cl.addLayout(grid)
            vlay.addWidget(card)

        # Result frame
        self.result_frame = QFrame()
        self.result_frame.setObjectName("resultLow")
        rl = QHBoxLayout(self.result_frame)
        rl.setContentsMargins(18,14,18,14)
        self.result_icon = QLabel("●")
        self.result_icon.setStyleSheet("font-size:28px;")
        self.result_risk = QLabel("—")
        self.result_risk.setStyleSheet("font-size:18px; font-weight:700;")
        self.result_prob = QLabel("")
        self.result_prob.setStyleSheet("font-size:13px; color:#475569;")
        self.result_note = QLabel("")
        self.result_note.setWordWrap(True)
        self.result_note.setStyleSheet("font-size:12px; color:#475569;")
        self.result_note.setFixedWidth(360)
        left = QVBoxLayout()
        left.addWidget(self.result_risk)
        left.addWidget(self.result_prob)
        rl.addWidget(self.result_icon)
        rl.addSpacing(12)
        rl.addLayout(left)
        rl.addSpacing(24)
        rl.addWidget(self.result_note)
        rl.addStretch()
        self.result_frame.hide()
        vlay.addWidget(self.result_frame)

        # Action buttons
        btn_row = QHBoxLayout(); btn_row.setSpacing(12)
        self.btn_analyze = QPushButton("Run Analysis")
        self.btn_analyze.setObjectName("btnPrimary")
        self.btn_analyze.setFixedHeight(42)
        self.btn_analyze.setCursor(Qt.PointingHandCursor)
        self.btn_analyze.clicked.connect(self._analyze)

        self.btn_save = QPushButton("Save to Database")
        self.btn_save.setObjectName("btnSecondary")
        self.btn_save.setFixedHeight(42)
        self.btn_save.setCursor(Qt.PointingHandCursor)
        self.btn_save.setEnabled(False)
        self.btn_save.clicked.connect(self._save)

        self.btn_pdf = QPushButton("Export PDF Report")
        self.btn_pdf.setObjectName("btnSecondary")
        self.btn_pdf.setFixedHeight(42)
        self.btn_pdf.setCursor(Qt.PointingHandCursor)
        self.btn_pdf.setEnabled(False)
        self.btn_pdf.clicked.connect(self._export_pdf)

        btn_row.addWidget(self.btn_analyze)
        btn_row.addWidget(self.btn_save)
        btn_row.addWidget(self.btn_pdf)
        btn_row.addStretch()
        vlay.addLayout(btn_row)
        vlay.addStretch()

        self.reset_for_new()

    def _collect_df(self):
        values = [field_value(n, self.inputs[n]) for n in feature_names]
        return pd.DataFrame([values], columns=feature_names)

    def _analyze(self):
        try:
            df   = self._collect_df()
            prob = model.predict_proba(df)[0][1]
            risk = "HIGH" if prob > 0.7 else "MEDIUM" if prob > 0.3 else "LOW"
            self._show_result(risk, prob)
            self.btn_save.setEnabled(True)
            self.btn_pdf.setEnabled(True)
        except Exception as e:
            QMessageBox.warning(self, "Input Error",
                f"Please check all fields contain valid numeric values.\n\nDetail: {e}")

    def _show_result(self, risk, prob):
        styles = {
            "HIGH":   ("resultHigh",   "#dc2626", "⚠", "High probability of peptic ulcer risk. Immediate evaluation advised."),
            "MEDIUM": ("resultMedium", "#d97706", "◉", "Moderate risk. Lifestyle changes and follow-up recommended."),
            "LOW":    ("resultLow",    "#16a34a", "✓", "Low risk indicators at this time. Continue routine monitoring."),
        }
        obj, color, icon, note = styles.get(risk, styles["LOW"])
        bg = ({'HIGH':'background:#fef2f2; border:2px solid #fca5a5;',
               'MEDIUM':'background:#fffbeb; border:2px solid #fcd34d;',
               'LOW':'background:#f0fdf4; border:2px solid #86efac;'}[risk])
        self.result_frame.setStyleSheet(f"QFrame {{ {bg} border-radius:12px; padding:14px; }}")
        self.result_icon.setText(icon)
        self.result_icon.setStyleSheet(f"font-size:28px; color:{color};")
        self.result_risk.setText(f"{risk} RISK")
        self.result_risk.setStyleSheet(f"font-size:18px; font-weight:700; color:{color};")
        self.result_prob.setText(f"Probability: {prob*100:.1f}%")
        self.result_note.setText(note)
        self.result_frame.show()
        mapping = {
            "Age":"age","Gender":"gender","Spicy_Food":"spicy_food",
            "Tobacco":"tobacco","Alcohol":"alcohol","Sleep_Hours":"sleep_hours",
            "Stress":"stress","Skip_Meals":"skip_meals","Soft_Drinks":"soft_drinks",
            "Empty_Stomach_Pain":"empty_stomach_pain",
            "Day1_pH_Before":"day1_ph_before","Day1_pH_After":"day1_ph_after",
            "Day2_pH_Before":"day2_ph_before","Day2_pH_After":"day2_ph_after",
        }
        self._result_data = {
            "risk_level": risk, "probability": prob,
            "patient_id": self._current_id,
            "name": self.name_input.text().strip() or "—",
            "created_at": datetime.now().isoformat(),
        }
        for fname, dkey in mapping.items():
            self._result_data[dkey] = field_value(fname, self.inputs[fname])

    def _save(self):
        if not self._result_data:
            return
        try:
            save_patient(self._result_data)
            QMessageBox.information(self, "Saved",
                f"Patient {self._current_id} saved to database successfully.")
            self.patient_saved.emit(self._current_id)
        except Exception as e:
            QMessageBox.critical(self, "Save Error", str(e))

    def _export_pdf(self):
        if not self._result_data:
            return
        if not REPORTLAB_AVAILABLE:
            QMessageBox.warning(self, "Missing Library",
                "reportlab is not installed.\nRun: pip install reportlab")
            return
        default_name = f"UlcerRisk_{self._current_id}_{datetime.now().strftime('%Y%m%d')}.pdf"
        path, _ = QFileDialog.getSaveFileName(
            self, "Save PDF Report",
            os.path.join(os.path.expanduser("~"), default_name),
            "PDF Files (*.pdf)"
        )
        if not path:
            return
        try:
            export_pdf(self._result_data, path)
            reply = QMessageBox.information(self, "PDF Exported",
                f"Report saved to:\n{path}\n\nWould you like to open it?",
                QMessageBox.Yes | QMessageBox.No)
            if reply == QMessageBox.Yes:
                if sys.platform == "win32":
                    os.startfile(path)
                elif sys.platform == "darwin":
                    os.system(f'open "{path}"')
                else:
                    os.system(f'xdg-open "{path}"')
        except Exception as e:
            QMessageBox.critical(self, "Export Error", str(e))


# ═══════════════════════════════════════════════════════════════════════════════
# PATIENT DATABASE PAGE
# ═══════════════════════════════════════════════════════════════════════════════
class DatabasePage(QWidget):
    open_patient = pyqtSignal(str)

    def __init__(self):
        super().__init__()
        self._build()

    def _build(self):
        root = QVBoxLayout(self)
        root.setContentsMargins(28,24,28,24)
        root.setSpacing(16)

        hr = QHBoxLayout()
        ttl = QLabel("Patient Database")
        ttl.setStyleSheet("font-size:20px; font-weight:700; color:#0f172a;")
        hr.addWidget(ttl)
        hr.addStretch()

        self.search = QLineEdit()
        self.search.setPlaceholderText("Search by name or ID…")
        self.search.setFixedWidth(240)
        self.search.setFixedHeight(36)
        self.search.textChanged.connect(self._filter)
        hr.addWidget(self.search)

        btn_refresh = QPushButton("↻  Refresh")
        btn_refresh.setObjectName("btnSecondary")
        btn_refresh.setFixedHeight(36)
        btn_refresh.setCursor(Qt.PointingHandCursor)
        btn_refresh.clicked.connect(self.refresh)
        hr.addWidget(btn_refresh)
        root.addLayout(hr)

        self.table = QTableWidget()
        self.table.setColumnCount(5)
        self.table.setHorizontalHeaderLabels(["Patient ID","Name","Risk Level","Probability","Date"])
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch)
        self.table.horizontalHeader().setSectionResizeMode(0, QHeaderView.ResizeToContents)
        self.table.setSelectionBehavior(QAbstractItemView.SelectRows)
        self.table.setEditTriggers(QAbstractItemView.NoEditTriggers)
        self.table.setAlternatingRowColors(True)
        self.table.setShowGrid(False)
        self.table.verticalHeader().hide()
        self.table.setStyleSheet("alternate-background-color: #f8fafc;")
        self.table.doubleClicked.connect(self._open_selected)
        root.addWidget(self.table)

        br = QHBoxLayout()
        self.btn_open = QPushButton("View Patient")
        self.btn_open.setObjectName("btnPrimary")
        self.btn_open.setFixedHeight(38)
        self.btn_open.setCursor(Qt.PointingHandCursor)
        self.btn_open.clicked.connect(self._open_selected)

        self.btn_delete = QPushButton("Delete Record")
        self.btn_delete.setObjectName("btnDanger")
        self.btn_delete.setFixedHeight(38)
        self.btn_delete.setCursor(Qt.PointingHandCursor)
        self.btn_delete.clicked.connect(self._delete_selected)

        self.count_lbl = QLabel("0 records")
        self.count_lbl.setStyleSheet("color:#64748b; font-size:12px;")

        br.addWidget(self.btn_open)
        br.addWidget(self.btn_delete)
        br.addStretch()
        br.addWidget(self.count_lbl)
        root.addLayout(br)

        self._all_rows = []
        self.refresh()

    def refresh(self):
        self._all_rows = load_all_patients()
        self._populate(self._all_rows)

    def _populate(self, rows):
        self.table.setRowCount(0)
        risk_colors = {"HIGH":"#dc2626","MEDIUM":"#d97706","LOW":"#16a34a"}
        for pid, name, risk, prob, created in rows:
            r = self.table.rowCount()
            self.table.insertRow(r)
            self.table.setItem(r, 0, QTableWidgetItem(pid))
            self.table.setItem(r, 1, QTableWidgetItem(name or "—"))
            risk_item = QTableWidgetItem(risk or "—")
            risk_item.setForeground(QColor(risk_colors.get(risk,"#1e293b")))
            risk_item.setFont(QFont("Segoe UI", 10, QFont.Bold))
            self.table.setItem(r, 2, risk_item)
            self.table.setItem(r, 3, QTableWidgetItem(f"{(prob or 0)*100:.1f}%"))
            dt = (created or "")[:16].replace("T"," ")
            self.table.setItem(r, 4, QTableWidgetItem(dt))
            self.table.setRowHeight(r, 40)
        self.count_lbl.setText(f"{len(rows)} record{'s' if len(rows)!=1 else ''}")

    def _filter(self, text):
        text = text.lower()
        filtered = [r for r in self._all_rows
                    if text in (r[0] or "").lower() or text in (r[1] or "").lower()]
        self._populate(filtered)

    def _get_selected_pid(self):
        row = self.table.currentRow()
        if row < 0:
            QMessageBox.information(self, "No Selection", "Please select a patient row first.")
            return None
        return self.table.item(row, 0).text()

    def _open_selected(self):
        pid = self._get_selected_pid()
        if pid:
            self.open_patient.emit(pid)

    def _delete_selected(self):
        pid = self._get_selected_pid()
        if not pid:
            return
        reply = QMessageBox.question(self, "Confirm Delete",
            f"Permanently delete record for {pid}?\nThis cannot be undone.",
            QMessageBox.Yes | QMessageBox.No)
        if reply == QMessageBox.Yes:
            delete_patient(pid)
            self.refresh()


# ═══════════════════════════════════════════════════════════════════════════════
# MAIN WINDOW  (now includes UpdateBanner)
# ═══════════════════════════════════════════════════════════════════════════════
class MainWindow(QWidget):
    def __init__(self):
        super().__init__()
        self.setWindowTitle(f"Ulcer Risk AI  ·  {CURRENT_VERSION}")
        self.setMinimumSize(1100, 720)
        self._build()

    def _build(self):
        root_v = QVBoxLayout(self)
        root_v.setContentsMargins(0,0,0,0)
        root_v.setSpacing(0)

        # ── Update banner (top of everything) ──────────────────────────────
        self.update_banner = UpdateBanner()
        root_v.addWidget(self.update_banner)

        # ── Main horizontal area ────────────────────────────────────────────
        h_area = QWidget()
        root_h = QHBoxLayout(h_area)
        root_h.setContentsMargins(0,0,0,0)
        root_h.setSpacing(0)
        root_v.addWidget(h_area)

        # Sidebar
        sidebar = QFrame(); sidebar.setObjectName("sidebar"); sidebar.setFixedWidth(220)
        sl = QVBoxLayout(sidebar)
        sl.setContentsMargins(12,24,12,24)
        sl.setSpacing(4)

        brand = QLabel("Ulcer Risk AI")
        # Updated style: Added 'letter-spacing' for a modern look and adjusted the gray tone
        brand.setStyleSheet(""" 
            color: #ffffff; 
            background: transparent; 
            font-size: 15px; 
            font-weight: 700; 
            padding: 0 4px 20px 4px; 
            letter-spacing: 0.5px; 
            text-transform: uppercase; 
        """)
        brand.setAlignment(Qt.AlignCenter)
        sl.addWidget(brand)


        self.nav_buttons = []
        for label, idx in [("🧪  New Assessment", 0), ("📋  Patient Database", 1)]:
            btn = QPushButton(label)
            btn.setCheckable(True)
            btn.setCursor(Qt.PointingHandCursor)
            btn.clicked.connect(lambda checked, i=idx: self._switch(i))
            sl.addWidget(btn)
            self.nav_buttons.append(btn)

        sl.addStretch()

        # "Check for updates" manual trigger in sidebar
        btn_upd = QPushButton("🔄  Check for Updates")
        btn_upd.setCursor(Qt.PointingHandCursor)
        btn_upd.clicked.connect(self.update_banner.start_check)
        sl.addWidget(btn_upd)

        ver = QLabel(f"Version {CURRENT_VERSION.lstrip('v')}")
        ver.setStyleSheet("color:#475569; background: transparent; trans font-size:11px; padding-left:4px;")
        sl.addWidget(ver)

        root_h.addWidget(sidebar)

        # Stack
        self.stack = QStackedWidget()
        root_h.addWidget(self.stack)

        self.form_page = NewPatientPage()
        self.form_page.patient_saved.connect(self._on_patient_saved)
        self.db_page   = DatabasePage()
        self.db_page.open_patient.connect(self._open_patient_in_form)

        self.stack.addWidget(self.form_page)
        self.stack.addWidget(self.db_page)
        self._switch(0)

        # Auto-check on startup after 2 s (non-blocking)
        QTimer.singleShot(2000, self.update_banner.start_check)

    def _switch(self, idx):
        self.stack.setCurrentIndex(idx)
        for i, btn in enumerate(self.nav_buttons):
            btn.setChecked(i == idx)
        if idx == 1:
            self.db_page.refresh()

    def _on_patient_saved(self, pid):
        self.db_page.refresh()

    def _open_patient_in_form(self, pid):
        self.form_page.load_patient(pid)
        self._switch(0)


# ═══════════════════════════════════════════════════════════════════════════════
# APP ENTRY
# ═══════════════════════════════════════════════════════════════════════════════
app = QApplication(sys.argv)
app.setStyleSheet(APP_STYLE)

ico_path = os.path.join(BASE_DIR, "assets", "app.ico")
if os.path.exists(ico_path):
    app.setWindowIcon(QIcon(ico_path))

main_win = MainWindow()

login_win = QWidget()
login_win.setWindowTitle("Ulcer Risk AI — Sign In")
login_win.setFixedSize(480, 520)
login_win.setStyleSheet("background:#f1f5f9;")
ll = QVBoxLayout(login_win)
ll.setContentsMargins(0,0,0,0)

def on_login_success():
    login_win.close()
    main_win.showMaximized()

login_page = LoginPage(on_login_success)
ll.addWidget(login_page)
login_win.show()

sys.exit(app.exec_())