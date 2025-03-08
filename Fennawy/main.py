import socket
import struct
import threading
import tkinter as tk
from PIL import Image, ImageTk  # Requires Pillow library

SERVER_IP = "0.0.0.0"
SERVER_PORT = 5001

class ServerApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Socket Server")
        self.root.geometry("300x200")

        self.status_label = tk.Label(root, text="Server: Stopped", font=("Arial", 12))
        self.status_label.pack(pady=10)

        self.toggle_button = tk.Button(root, text="Start Server", command=self.toggle_server,
                                       font=("Arial", 12), bg="red", fg="white", width=15)
        self.toggle_button.pack(pady=10)

        self.capture_button = tk.Button(root, text="Capture", command=self.send_capture_command,
                                        font=("Arial", 12), bg="blue", fg="white", width=15, state="disabled")
        self.capture_button.pack(pady=10)

        self.server_socket = None
        self.client_socket = None
        self.is_running = False
        self.server_thread = None
        
        # Image label definition
        self.image_label = tk.Label(root)
        self.image_label.pack(pady=10)
        self.current_image = None  # Keep reference to prevent garbage collection
        

    def toggle_server(self):
        if self.is_running:
            self.stop_server()
        else:
            self.start_server()

    def start_server(self):
        try:
            self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            self.server_socket.bind((SERVER_IP, SERVER_PORT))
            self.server_socket.listen(1)

            self.is_running = True
            self.status_label.config(text="Server: Running")
            self.toggle_button.config(text="Stop Server", bg="green")

            self.server_thread = threading.Thread(target=self.accept_connection, daemon=True)
            self.server_thread.start()

        except Exception as e:
            print(f"Error starting server: {e}")

    def accept_connection(self):
        print("Waiting for connection...")
        try:
            self.client_socket, addr = self.server_socket.accept()
            print(f"Connected by {addr}")
            self.capture_button.config(state="normal")

            # Start a separate thread to listen for incoming images
            threading.Thread(target=self.receive_image, daemon=True).start()

        except Exception as e:
            if self.is_running:
                print(f"Error accepting connection: {e}")

    def send_capture_command(self):
        if self.client_socket:
            try:
                msg = "capture\n"
                self.client_socket.sendall(msg.encode())
                print(f"Sent: {msg.strip()}")
            except Exception as e:
                print(f"Error sending message: {e}")

    def receive_image(self):
        try:
            while True:
                # Read exactly 4 bytes for the image size header
                print("Receiving image size header")
                header = b""
                while len(header) < 4:
                    print("len header<4,",len(header))
                    chunk = self.client_socket.recv(4 - len(header))
                    if not chunk:
                        break
                    header += chunk
                
                if len(header) != 4:
                    print("Incomplete size header received")
                    break

                img_size = struct.unpack(">I", header)[0]  # BIG_ENDIAN format
                
                # Read image data in chunks until full size is received
                img_data = b""
                remaining = img_size
                while remaining > 0:
                    chunk = self.client_socket.recv(min(4096, remaining))
                    if not chunk:
                        break
                    img_data += chunk
                    remaining -= len(chunk)
                
                if len(img_data) == img_size:
                    print("Image data received")
                    self.save_and_display_image(img_data)
                else:
                    print("Incomplete image data received")
                    
        except Exception as e:
            print(f"Error receiving image: {e}")


    def save_and_display_image(self, img_data):
        try:
            # Save to file
            with open("received_image.jpg", "wb") as f:
                f.write(img_data)
            
            # Load image with Pillow
            img = Image.open("received_image.jpg")

            img.thumbnail((400,400))  #resize image to fit in GUI keeping aspect ratio          
            

            # Convert to Tkinter PhotoImage
            self.current_image = ImageTk.PhotoImage(img)
            
            # Update GUI in main thread
            self.root.after(0, self.update_image_display)
            
        except Exception as e:
            print(f"Error processing image: {e}")

    def update_image_display(self):
        self.image_label.config(image=self.current_image)
        self.image_label.image = self.current_image  # Keep reference

        width, height = self.current_image.width(), self.current_image.height()
        
        # Resize the Tkinter window to fit the image
        self.root.geometry(f"{width}x{height+200}")
        print("Image displayed in GUI")

    def stop_server(self):
        self.is_running = False

        if self.client_socket:
            try:
                self.client_socket.close()
            except Exception as e:
                print(f"Error closing client socket: {e}")
            self.client_socket = None

        if self.server_socket:
            try:
                self.server_socket.close()
            except Exception as e:
                print(f"Error closing server socket: {e}")
            self.server_socket = None

        self.status_label.config(text="Server: Stopped")
        self.toggle_button.config(text="Start Server", bg="red")
        self.capture_button.config(state="disabled")


root = tk.Tk()
app = ServerApp(root)

root.mainloop()
