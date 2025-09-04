import asyncio
import websockets
import base64
import queue
import numpy as np
import sounddevice as sd
import RPi.GPIO as GPIO
import pigpio
import cv2
from picamera2 import Picamera2
import time

IN1 = 17
IN2 = 18

IN3 = 22
IN4 = 23

IN5 = 25
IN6 = 8

IN7 = 7
IN8 = 1

PIN_CAM_UPDOWN = 5
PIN_CAM_LEFTRIGHT = 6
GPIO.setmode(GPIO.BCM)
GPIO.setwarnings(False)

for pin in [IN1, IN2, IN3, IN4, IN5, IN6, IN7, IN8, PIN_CAM_UPDOWN, PIN_CAM_LEFTRIGHT]:
    GPIO.setup(pin, GPIO.OUT)

pi = pigpio.pi()
if not pi.connected:
    print("pigpio daemon is not running.")
    exit()

picam2 = Picamera2()
picam2.configure(picam2.create_video_configuration(main={"size": (640, 360)}))
picam2.start()
time.sleep(2)

SAMPLE_RATE = 16000
DTYPE = 'int16'
CHANNELS = 1
audio_queue = queue.Queue()

angle_updown = 150
angle_leftright = 90

def set_angle(pin, angle):
    angle = max(0, min(180, angle))
    if pin == PIN_CAM_UPDOWN:
        angle = max(95, min(180, angle))
    pulse_width = int(500 + (angle / 180.0) * 2000)
    pi.set_servo_pulsewidth(pin, pulse_width)
    return angle

set_angle(PIN_CAM_UPDOWN, angle_updown)
set_angle(PIN_CAM_LEFTRIGHT, angle_leftright)

def stop_all():
    for pin in [IN1, IN2, IN3, IN4, IN5, IN6, IN7, IN8]:
        GPIO.output(pin, GPIO.LOW)

async def handle_video(websocket):
    print("Video connection established.")
    while websocket.open:
        try:
            frame = picam2.capture_array()
            frame_bgr = cv2.cvtColor(frame, cv2.COLOR_RGB2BGR)
            _, buffer = cv2.imencode('.jpg', frame_bgr, [cv2.IMWRITE_JPEG_QUALITY, 60])
            jpg_base64 = base64.b64encode(buffer).decode('utf-8')
            await websocket.send(f"IMAGE:{jpg_base64}")
            await asyncio.sleep(1 / 60)
        except Exception as e:
            print(f"[Video] Error: {e}")
            break

def audio_playback():
    stream = sd.OutputStream(samplerate=SAMPLE_RATE, channels=CHANNELS, dtype=DTYPE)
    stream.start()
    while True:
        audio_array = audio_queue.get()
        if audio_array is None:
            break
        try:
            stream.write(audio_array)
        except Exception as e:
            print(f"[Audio] Write error: {e}")
    stream.stop()
    stream.close()

audio_thread = asyncio.to_thread(audio_playback)

async def handle_audio(websocket):
    print("Audio connection established.")
    async for message in websocket:
        if message.startswith("AUDIO:"):
            try:
                data = base64.b64decode(message.replace("AUDIO:", ""))
                audio_array = np.frombuffer(data, dtype=DTYPE)
                if audio_queue.full():
                    audio_queue.get_nowait()
                audio_queue.put(audio_array)
            except Exception as e:
                print(f"[Audio] Audio decoding error: {e}")

async def handle_commands(websocket):
    print("Command connection established.")
    global angle_updown, angle_leftright
    current_cam_command = "stop"
    async def camera_control_loop():
        nonlocal current_cam_command
        global angle_updown, angle_leftright
        while True:
            if current_cam_command == "cam_up":
                angle_updown = set_angle(PIN_CAM_UPDOWN, angle_updown - 5)
            elif current_cam_command == "cam_down":
                angle_updown = set_angle(PIN_CAM_UPDOWN, angle_updown + 5)
            elif current_cam_command == "cam_right":
                angle_leftright = set_angle(PIN_CAM_LEFTRIGHT, angle_leftright - 5)
            elif current_cam_command == "cam_left":
                angle_leftright = set_angle(PIN_CAM_LEFTRIGHT, angle_leftright + 5)
            await asyncio.sleep(0.05)
    async def send_command_app():
        while websocket.open:
            nonlocal current_cam_command
            global angle_updown, angle_leftright
            try:
                if current_cam_command == "cam_up":
                    await websocket.send(f"angel_up:{angle_updown}")
                elif current_cam_command == "cam_down":
                    await websocket.send(f"angel_down:{angle_updown}")
                elif current_cam_command == "cam_right":
                    print(f"angel:{angle_leftright}")
                    await websocket.send(f"angel_right:{angle_leftright}")
                elif current_cam_command == "cam_left":
                    print(f"angel:{angle_leftright}")
                    await websocket.send(f"angel_left:{angle_leftright}")
                await asyncio.sleep(0.05)
            except Exception as e:
                print(f"Error: {e}")
    cam_control_task = asyncio.create_task(camera_control_loop())
    command_task = asyncio.create_task(send_command_app())
    
    async for message in websocket:
        print(f"[Command] Received message: {message}")
        if message == "forward":
            GPIO.output(IN1, GPIO.LOW)
            GPIO.output(IN2, GPIO.HIGH)
            GPIO.output(IN3, GPIO.LOW)
            GPIO.output(IN4, GPIO.HIGH)
            GPIO.output(IN5, GPIO.LOW)
            GPIO.output(IN6, GPIO.HIGH)
            GPIO.output(IN7, GPIO.LOW)
            GPIO.output(IN8, GPIO.HIGH)
        elif message == "backward":
            GPIO.output(IN1, GPIO.HIGH)
            GPIO.output(IN2, GPIO.LOW)
            GPIO.output(IN3, GPIO.HIGH)
            GPIO.output(IN4, GPIO.LOW)
            GPIO.output(IN5, GPIO.HIGH)
            GPIO.output(IN6, GPIO.LOW)
            GPIO.output(IN7, GPIO.HIGH)
            GPIO.output(IN8, GPIO.LOW)
        elif message == "left":
            GPIO.output(IN1, GPIO.LOW)
            GPIO.output(IN2, GPIO.HIGH)
            GPIO.output(IN3, GPIO.HIGH)
            GPIO.output(IN4, GPIO.LOW)
            GPIO.output(IN5, GPIO.HIGH)
            GPIO.output(IN6, GPIO.LOW)
            GPIO.output(IN7, GPIO.LOW)
            GPIO.output(IN8, GPIO.HIGH)
        elif message == "right":
            GPIO.output(IN1, GPIO.HIGH)
            GPIO.output(IN2, GPIO.LOW)
            GPIO.output(IN3, GPIO.LOW)
            GPIO.output(IN4, GPIO.HIGH)
            GPIO.output(IN5, GPIO.LOW)
            GPIO.output(IN6, GPIO.HIGH)
            GPIO.output(IN7, GPIO.HIGH)
            GPIO.output(IN8, GPIO.LOW)
        elif message == "stop":
            stop_all()
        if message in ["cam_up", "cam_down", "cam_left", "cam_right"]:
            current_cam_command = message
        elif message == "stop_cam":
            current_cam_command = "stop"
        elif message == "stop":
            current_cam_command = "stop"

async def main():
    video_server = await websockets.serve(handle_video, "0.0.0.0", 8080)
    audio_server = await websockets.serve(handle_audio, "0.0.0.0", 8081)
    command_server = await websockets.serve(handle_commands, "0.0.0.0", 8082)
    thread = await audio_thread
    print("Servers started.")
    try:
        await asyncio.Future()
    except asyncio.CancelledError:
        print("Server tasks cancelled.")
    finally:
        video_server.close()
        audio_server.close()
        command_server.close()
        thread.close()
        await video_server.wait_closed()
        await audio_server.wait_closed()
        await command_server.wait_closed()
        await thread.wait_closed()

if __name__ == "__main__":
    loop = asyncio.get_event_loop()
    main_task = loop.create_task(main())
    try:
        loop.run_until_complete(main_task)
    except KeyboardInterrupt:
        print("Server is shutting down...")
    finally:
        loop.close()
        stop_all()
        pi.stop()
        picam2.stop()
        picam2.close()
        audio_queue.put(None)
        GPIO.cleanup()
