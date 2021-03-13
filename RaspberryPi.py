# GUI, QR reader, UDPAudio

from tkinter import *
from threading import Thread
from http.server import BaseHTTPRequestHandler, HTTPServer
import tkinter.ttk
import tkinter.messagebox
import cv2
import pyaudio
import wave
import socket
import io
import time
import picamera

# door == 0 means the door is closed
# door == 1 means the door is opened
door = 0

# get password list from android
password_list = ["Prof. Yang09", "nice man Yang09", "handsome man Yang09"]

# VOIP variables
framesRx = []
framesTx = []
localhost = "192.168.137.111"
destination = "192.168.137.43"
port = 50005
FORMAT = pyaudio.paInt16
RxCHUNK = 1764
TxCHUNK = 1024
CHANNELS = 1
RATE = 44100
p = pyaudio.PyAudio()
streamRx = p.open(format=FORMAT,
                  channels=CHANNELS,
                  rate=RATE,
                  input=True,
                  frames_per_buffer=RxCHUNK)
streamTx = p.open(format=FORMAT,
                  channels=CHANNELS,
                  rate=RATE,
                  output=True,
                  frames_per_buffer=TxCHUNK)
isCalling = False
# To avoid audio buffer underrun define data of silence
silence = chr(0) * RxCHUNK * 2
# Video streaming variable
camera = None
videoServer = None


# Simple MJPEG streamer for Raspberry pi 3 camera
# Reference : https://gist.github.com/scilaci/1e11eb93405c180d6e247064a7fa78ea
class CamHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.endswith('.mjpg'):
            self.send_response(200)
            self.send_header('Content-type', 'multipart/x-mixed-replace; boundary=--jpgboundary')
            self.end_headers()
            stream = io.BytesIO()
            try:
                start = time.time()
                for foo in camera.capture_continuous(stream, 'jpeg', use_video_port=True):
                    self.wfile.write(bytes("--jpgboundary", "utf8"))
                    self.send_header('Content-type', 'image/jpeg')
                    self.send_header('Content-length', len(stream.getvalue()))
                    self.end_headers()
                    self.wfile.write(stream.getvalue())
                    stream.seek(0)
                    stream.truncate()
                    # time.sleep(.5)
            except KeyboardInterrupt:
                pass
            return
        else:
            self.send_response(200)
            self.send_header('Content-type', 'text/html')
            self.end_headers()
            self.wfile.write(
                bytes("<html><head></head><body><img src='/cam.mjpg' width=100%/></body></html>",
                      "utf8"))
            return


def run_video_stream():
    global camera, videoServer, videoServer
    print("Video server initializing")
    # Setup picamera
    camera = picamera.PiCamera()
    camera.resolution = (640, 480)
    try:
        # Start MJPEG streaming server
        videoServer = HTTPServer(('', 8080), CamHandler)
        print("Video server started")
        videoServer.serve_forever()
    except:
        print("Error occurred on video server")


def stop_video_stream():
    global camera, videoServer
    try:
        camera.close()
        videoServer.socket.close()
        videoServer.shundown()
        del videoServer
    except:
        print("Error on video server close")

# QR scanner code
def scan_QR():
    # set up camera object
    cap = cv2.VideoCapture(0)
    # QR code detection object
    detector = cv2.QRCodeDetector()
    flag_open = 0

    while True:
        # get the image
        _, img = cap.read()
        # get bounding box coords and data
        data, bbox, _ = detector.detectAndDecode(img)
        # if there is a bounding box, draw one, along with the data
        if bbox is not None:
            for i in range(len(bbox)):
                cv2.line(img, tuple(bbox[i][0]), tuple(bbox[(i + 1) % len(bbox)][0]),
                         color=(255, 0, 255), thickness=2)
            cv2.putText(img, data, (int(bbox[0][0][0]), int(bbox[0][0][1]) - 10),
                        cv2.FONT_HERSHEY_SIMPLEX,0.5, (0, 255, 0), 2)
            if data:
                pw_match = 0
                match_index = -1
                # checking password list
		for i in range(len(password_list)):
                    if password_list[i] == data:
                        pw_match = 1
                        match_index = i
		# if password is right, open the door and remove the password
                if pw_match == 1:
                    flag_open = 1
                    if match_index != -1:
                        del password_list[match_index]
                    break
                else:
                    flag_open = 0
                    break
                    # display the image preview
        winname = "code detector"
        cv2.namedWindow(winname)
        cv2.moveWindow(winname, 0, 0)
        cv2.imshow(winname, img)
        if cv2.waitKey(1) == ord("q"):
            break
            # free camera object and exit
    cap.release()
    cv2.destroyAllWindows()
    if flag_open == 1:
        Welcome()
    else:
        Try_again()


# To make calling gui close by command from cellphone,
# define toplevel variable as global
toplevel = None


# Sequence when guest rings bell
def ring_bell():
    # Define calling interface
    global toplevel
    toplevel = tkinter.Toplevel(win)
    toplevel.title("Ringing the bell")
    toplevel.geometry('800x420+0+0')
    # Set calling state on
    global isCalling
    isCalling = True
    # Define 4 threads for calling and start them
    TRx = Thread(target=udpReceive, args=(RxCHUNK,))
    TRxPlay = Thread(target=udpReceivePlay, args=(streamTx, RxCHUNK,))
    TRx.setDaemon(True)
    TRxPlay.setDaemon(True)
    TTx = Thread(target=udpTransmit)
    TTxRecord = Thread(target=udpTransmitRecord, args=(streamRx, TxCHUNK,))
    TTx.setDaemon(True)
    TTxRecord.setDaemon(True)
    TRx.start()
    TRxPlay.start()
    TTxRecord.start()
    TTx.start()
    # Define thread for video stream
    TVideo = Thread(target=run_video_stream)
    TVideo.setDaemon(True)
    TVideo.start()
    # Send command to android to start calling
    send_command("SCALL")
    # Define button to exit calling interface
    btn = Button(toplevel, width=50, text='\n\n\nEXIT\n\n\n', command=exit_btn)
    btn.pack(side="bottom")


# Action on exit button click in calling interface
def exit_btn():
    global isCalling
    if isCalling:
        isCalling = False
        toplevel.destroy()
        toplevel.update()
        stop_video_stream()


# Send command to android by UDP packet
def send_command(test):
    udp = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    udp.sendto(test.encode(), (destination, port + 2))
    print(test, "has been sent")
    udp.close()


# Start server receives command from android
def start_command_server():
    server = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    server.bind((localhost, port + 3))
    while True:
        data, addr = server.recvfrom(64)
        command = data.decode()
        print("Command", command, "received")
        # When owner permitted guest to come in on calling interface
        if command[:5] == "DOPEN":
            exit_btn()
            Welcome()
        # When android request to generate temporary key
        elif command[:5] == "KYGEN":
            password_list.append(command[5:15])
            send_command("KYCNF" + command[5:15])


# Start audio packet receiving UDP server
def udpReceive(CHUNK):
    # Bind UDP socket
    udp = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    udp.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    udp.bind((localhost, port))
    # Set timeout to release receiving blocking state
    udp.settimeout(0.5)
    # Call audio receiving buffer
    global framesRx
    print("Audio receiving socket bind")
    # Packet counter variable for debugging
    pCounter = 0
    # Controlled by streaming state variable
    while isCalling:
        try:
            # Receive packet and store it in buffer
            soundData, addr = udp.recvfrom(CHUNK * CHANNELS * 4)
            framesRx.append(soundData)
            pCounter += 1
            if pCounter >= 50:
                print("50 packets received")
                pCounter = 0
        except:
            print("Audio receiving timeout")
    # After close, clear variables
    framesRx = []
    udp.detach()
    udp.close()
    del udp


# Start audio packet transmitting server
def udpTransmit():
    # Bind socket
    udp1 = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    pCounter = 0
    while isCalling:
        # When buffer receives data from mic, transmit to UDP port
        if len(framesTx) > 0:
            udp1.sendto(framesTx.pop(0), (destination, port + 1))
            pCounter += 1
            if pCounter >= 50:
                print("50 Packets are Transmitted")
                pCounter = 0
    udp1.detach()
    udp1.close()
    del udp1


# Record voice and put it into buffer
def udpTransmitRecord(stream, CHUNK):
    while isCalling:
        framesTx.append(stream.read(CHUNK))


# Play audio from receiving buffer
def udpReceivePlay(stream, CHUNK):
    # Define minimum buffer size to avoid malfunction by UDP packet lost
    BUFFER = 5
    # Variable for error occurrence check for debugging
    error_state = False
    # Controlled by streaming state
    while isCalling:
        # Check buffer size is enough
        if len(framesRx) >= BUFFER:
            while isCalling:
                try:
                    stream.write(framesRx.pop(0), CHUNK)
                    if error_state:
                        error_state = False
                        print("audio out ")
                except:
                    if not error_state:
                        error_state = True
                        print("Play Error")
                    stream.write(silence, CHUNK)
        # If buffer is not enough, output silence
        else:
            stream.write(silence, CHUNK)


# When door is opened.(by QR or command from phone)
def Welcome():
    # Command notice phone that door is opened
    send_command("CRTQR")
    tkinter.messagebox.showinfo("Welcome", "The door is opened")


# When door get wrong QR
def Try_again():
    # Command notice phone that wrong QR is inputted
    send_command("INCQR")
    tkinter.messagebox.showinfo("Sorry", "Wrong QR code,\nplease Try again")


# When delivery is arrived
def delivery():
    # Command notice phone that something is delivered
    send_command("FEDEX")
    tkinter.messagebox.showinfo("Thank you", "Thank you for fast delivery\nPlease put it at the door")


# Start command receiving thread
cth = Thread(target=start_command_server)
cth.setDaemon(True)
cth.start()

win = Tk()
win.title("Raspberry Pi UI")
win.geometry('800x420+0+0')

action3 = tkinter.ttk.Button(win, width=50, text="\n\n\n\nDelivery service\n\n\n\n", command=delivery)
action3.pack(side="bottom", fill="x")

action1 = tkinter.ttk.Button(win, width=50, text="QR Scan for Open", command=scan_QR)
action1.pack(side="left", fill="y")

action2 = tkinter.ttk.Button(win, width=50, text="Ring the bell", command=ring_bell)
action2.pack(side="right", fill="y")

win.mainloop()
