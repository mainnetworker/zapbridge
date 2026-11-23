"""ZapBridge PC window: runs the server and shows the PIN, address and activity log."""
import os
import queue
import socket
import sys
import threading
import tkinter as tk

import server

log = queue.Queue()


class ToLog:
    def write(self, s):
        if s.strip():
            log.put(s.rstrip())

    def flush(self):
        pass


def local_ip():
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("10.255.255.255", 1))  # no packet is sent; just picks the LAN interface
            return s.getsockname()[0]
    except OSError:
        return "tarmoq yo'q"


def main():
    sys.stdout = sys.stderr = ToLog()  # --noconsole exe has no stdout; show it in the window
    threading.Thread(target=server.main, daemon=True).start()

    root = tk.Tk()
    root.title("ZapBridge")
    root.geometry("420x360")
    tk.Label(root, text="Telefondagi ZapBridge'ga shu PIN'ni kiriting:", font=("Segoe UI", 10)).pack(pady=(16, 0))
    tk.Label(root, text=server.PIN, font=("Segoe UI", 36, "bold"), fg="#2563eb").pack()
    tk.Label(root, text=f"PC manzili: {local_ip()}   (telefon bir xil Wi-Fi'da bo'lsin)",
             font=("Segoe UI", 9), fg="#555").pack()
    tk.Button(root, text="Ulashilgan papkani ochish",
              command=lambda: os.startfile(server.SHARE_DIR)).pack(pady=10)
    box = tk.Text(root, height=9, font=("Consolas", 9), state="disabled", bg="#f4f4f5", relief="flat")
    box.pack(fill="both", expand=True, padx=12, pady=(0, 12))

    def pump():
        while not log.empty():
            box.configure(state="normal")
            box.insert("end", log.get() + "\n")
            box.see("end")
            box.configure(state="disabled")
        root.after(200, pump)

    pump()
    root.mainloop()


if __name__ == "__main__":
    main()
