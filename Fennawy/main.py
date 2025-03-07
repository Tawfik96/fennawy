import socket
import threading
import tkinter as tk

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

    def toggle_server(self):
        """Start or stop the server when the button is clicked."""
        if self.is_running:
            self.stop_server()
        else:
            self.start_server()

    def start_server(self):
        """Initialize the server and start listening for connections."""
        try:
            self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)  # Allow quick reuse of port
            self.server_socket.bind((SERVER_IP, SERVER_PORT))
            self.server_socket.listen(1)  # Allow only one connection

            self.is_running = True
            self.status_label.config(text="Server: Running")
            self.toggle_button.config(text="Stop Server", bg="green")

            # Start the server thread to accept connections
            self.server_thread = threading.Thread(target=self.accept_connection, daemon=True)
            self.server_thread.start()

        except Exception as e:
            print(f"Error starting server: {e}")

    def accept_connection(self):
        """Accept a connection from a client (Android app)."""
        print("Waiting for connection...")
        try:
            self.client_socket, addr = self.server_socket.accept()
            print(f"Connected by {addr}")
            self.capture_button.config(state="normal")  # Enable capture button
        except Exception as e:
            if self.is_running:  # Only log error if server is still supposed to be running
                print(f"Error accepting connection: {e}")

    def send_capture_command(self):
        """Send 'capture' command to the connected client."""
        if self.client_socket:
            try:
                msg = "capture\n"  # Add newline to ensure complete message
                self.client_socket.sendall(msg.encode())
                print(f"Sent: {msg.strip()}")
            except Exception as e:
                print(f"Error sending message: {e}")

    def stop_server(self):
        """Stop the server and close connections properly."""
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
