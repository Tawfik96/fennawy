import socket

HOST = "0.0.0.0"  # Listen on all available network interfaces
PORT = 5001      

server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server_socket.bind((HOST, PORT))
server_socket.listen(1)

print("Waiting for connection...")
conn, addr = server_socket.accept()
print(f"Connected by {addr}")

while True:
    msg = input("Enter command: ")  # Type "capture" to trigger the camera
    if not msg:
        continue

    msg += "\n"  # Add newline so Android reads the full message
    conn.sendall(msg.encode())
    print(f"Sent: {msg.strip()}")

    if msg.strip().lower() == "exit":
        break

conn.close()
server_socket.close()
