from PyQt5.QtWidgets import QSplashScreen, QApplication
from PyQt5.QtGui import QPixmap
from PyQt5.QtCore import Qt, QTimer

def show_splash(app):
    splash = QSplashScreen(QPixmap("splash.png"))
    splash.setWindowFlag(Qt.WindowStaysOnTopHint)
    splash.show()

    # Auto close after 2 seconds
    QTimer.singleShot(2000, splash.close)

    return splash