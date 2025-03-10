import socket
import struct
import threading
import os
import tkinter as tk
import datetime
from PIL import Image, ImageTk, ImageDraw
import chess
import chess.svg
import cairosvg  # Needed for SVG to PNG conversion

SERVER_IP = "0.0.0.0"   #my ip is 192.168.167.176 on the hotspot
SERVER_PORT = 5001

class ServerApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Socket Server")
        self.root.geometry("600x600")

        self.save_dir = "SavedImages"
        os.makedirs(self.save_dir, exist_ok=True)
        self.saved_count = 0
        self.current_fen=chess.STARTING_FEN
        # Status Label
        self.status_label = tk.Label(root, text="Server: Stopped", font=("Arial", 12))
        self.status_label.pack(pady=10)

        # Button Frame
        button_frame = tk.Frame(root)
        button_frame.pack(pady=5)

        self.toggle_button = tk.Button(button_frame, text="Start Server", command=self.toggle_server,
                                      font=("Arial", 12), bg="red", fg="white", width=15)
        self.toggle_button.pack(side=tk.LEFT, padx=5)

        self.capture_button = tk.Button(button_frame, text="Capture", command=self.send_capture_command,
                                       font=("Arial", 12), bg="blue", fg="white", width=15, state="disabled")
        self.capture_button.pack(side=tk.LEFT, padx=5)

        self.save_button = tk.Button(root, text="Save Image", command=self.save_current_image,
                                    font=("Arial", 12), bg="black", fg="white", width=15, state="disabled")
        self.save_button.pack(pady=5)

        # Counter Label
        self.counter_label = tk.Label(root, text="Saved Images: 0", font=("Arial", 12))
        self.counter_label.pack(pady=10)

        # Image Labels (Existing Image and Chessboard)
        self.image_frame = tk.Frame(root)
        self.image_frame.pack(pady=10)

        # Existing Image View
        self.image_label = tk.Label(self.image_frame, text="Captured Image")
        self.image_label.pack(side=tk.LEFT, padx=20)
        self.current_image = None
        self.current_image_data = None

        # Chessboard View
        self.chess_label = tk.Label(self.image_frame, text="Chessboard (FEN)")
        self.chess_label.pack(side=tk.RIGHT, padx=20)
        self.chess_image = None

        self.server_socket = None
        self.client_socket = None
        self.is_running = False
        self.server_thread = None

        # Load Initial Chessboard
        self.load_chessboard()

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
                header = b""
                while len(header) < 4:
                    chunk = self.client_socket.recv(4 - len(header))
                    if not chunk:
                        break
                    header += chunk

                if len(header) != 4:
                    print("Incomplete size header received")
                    break

                img_size = struct.unpack(">I", header)[0]
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
                    self.current_image_data = img_data
                    self.save_and_display_image(img_data)
                else:
                    print("Incomplete image data received")

        except Exception as e:
            print(f"Error receiving image: {e}")

    def save_and_display_image(self, img_data):
        try:
            with open("received_image.jpg", "wb") as f:
                f.write(img_data)

            img = Image.open("received_image.jpg")
            img.thumbnail((300, 300))

            self.save_button.config(state="normal")
            self.current_image = ImageTk.PhotoImage(img)

            self.root.after(0, self.update_image_display)
        except Exception as e:
            print(f"Error processing image: {e}")

    def save_current_image(self):
        if self.current_image_data:
            try:
                latest_id = 1  # Default ID if file is empty
                try:
                    with open("Labeled_FENs.txt", "r") as f:
                        lines = f.readlines()
                    if lines:
                        last_line = lines[-1].strip()  # Get last line
                        parts = last_line.split(".", 1)  # Split at the first '.'
                        if parts[0].isdigit():
                            latest_id = int(parts[0])+1  # Increment latest ID
                except FileNotFoundError:
                    pass  # If file doesn't exist, start from 1

                # Step 2: Generate filename using latest_id
                timestamp = datetime.datetime.now().strftime("%d_%H%M%S")
                filename = os.path.join(self.save_dir, f"image_{latest_id}_{timestamp}.jpg")

                # Step 3: Save the image
                with open(filename, "wb") as f:
                    f.write(self.current_image_data)

                # Step 4: Append new FEN entry to Latest_FENs.txt
                with open("Labeled_FENs.txt", "a") as f:
                    f.write(f"{latest_id}.#({self.current_fen})\n")

                # Step 5: Update counter
                self.saved_count += 1
                self.counter_label.config(text=f"Saved Images: {self.saved_count}")

                self.current_image_data = None
                self.current_image = None
                self.image_label.config(image=None)
                self.save_button.config(state="disabled") 
                self.update_image_display()


                print(f"Image saved as: {filename}")
                self.delete_fen()  # Delete the FEN after saving the image
                print(f"Fen {self.current_fen} is deleted")

            except Exception as e:
                print(f"Error saving image: {e}")


    def update_image_display(self):
        self.image_label.config(image=self.current_image)
        self.image_label.image = self.current_image

    def load_chessboard(self):
        # Read FEN from a file and delete it after reading
        # Read FEN from a file
        try:
            with open("fens.txt", "r") as f:
                fen = f.readline().strip()
        except FileNotFoundError:
            fen = chess.STARTING_FEN  # Default to standard chess start position

        self.current_fen=fen
        
        # Generate SVG of chessboard
        board = chess.Board(fen)
        board_svg = chess.svg.board(board=board, size=400)

        # Convert SVG to PNG
        cairosvg.svg2png(bytestring=board_svg, write_to="chessboard.png")

        # Load and display chessboard
        img = Image.open("chessboard.png")
        img.thumbnail((300, 300))
        self.chess_image = ImageTk.PhotoImage(img)

        self.chess_label.config(image=self.chess_image)
        self.chess_label.image = self.chess_image
    
    def delete_fen(self):
        # Read FEN from a file and delete it after reading
        try:
            with open("fens.txt", "r") as f:
                lines = f.readlines()  # Read all lines (FENs)
            
            if not lines:
                fen = chess.STARTING_FEN  # Default to standard chess start position
            else:
                fen = lines[0].strip()  # Get the first FEN
                print(fen)
                remaining_fens = lines[1:]  # Remove the first FEN

                # Write the remaining FENs back to the file
                with open("fens.txt", "w") as f:
                    f.writelines(remaining_fens)
                    
        except FileNotFoundError:
            fen = chess.STARTING_FEN  # Default if file doesn't exist

        
        self.load_chessboard()
        
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
        self.save_button.config(state="disabled")


# Run GUI
root = tk.Tk()
app = ServerApp(root)
root.mainloop()
