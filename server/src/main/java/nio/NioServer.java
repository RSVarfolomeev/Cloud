package nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class NioServer {

    private final ByteBuffer buffer = ByteBuffer.allocate(256);
    private String path = "server/serverFiles";
    private String varPath = path;
    private ServerSocketChannel serverChannel;
    private Selector selector;

    public NioServer() throws IOException {
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(8189));
        selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("Server started");
        while (serverChannel.isOpen()) {
            selector.select(); // block
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = keys.iterator();
            while (keyIterator.hasNext()) {
                SelectionKey selectionKey = keyIterator.next();
                if (selectionKey.isAcceptable()) {
                    System.out.println("Client accepted");
                    processAccept();
                }
                if (selectionKey.isReadable()) {
                    processRead(selectionKey);
                }
                keyIterator.remove();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        new NioServer();
    }

    private void processRead(SelectionKey selectionKey) throws IOException {
        SocketChannel channel = (SocketChannel) selectionKey.channel();
        int read = channel.read(buffer);
        if (read == -1) {
            channel.close();
        }
        if (read > 0) {
            buffer.flip();
            StringBuilder msg = new StringBuilder();
            while (buffer.hasRemaining()) {
                msg.append((char) buffer.get());
            }
            System.out.println("Received: " + msg);
            buffer.clear();
            String message = msg.toString().trim();

            if (message.equals("--list")) {
                channel.write(ByteBuffer.wrap("echo\r\ncat\r\ncd\r\ntouch\r\nls\r\nmkdir\r\nwrite\r\n".getBytes(StandardCharsets.UTF_8)));
            } else if (message.startsWith("echo")) {
                String echo = message.replace("echo ", "") + "\r\n";
                channel.write(ByteBuffer.wrap(echo.getBytes(StandardCharsets.UTF_8)));
            } else if (message.startsWith("cat")) {
                String fileName = message.replace("cat ", "");
                String catPath = varPath + "\\" + fileName;
                List<String> info = Files.readAllLines(Path.of(catPath));
                for (int i = 0; i < info.size(); i++) {
                    info.set(i, info.get(i) + "\r\n");
                    channel.write(ByteBuffer.wrap(info.get(i).getBytes(StandardCharsets.UTF_8)));
                }
            } else if (message.startsWith("cd")) {
                String newPath = message.replace("cd ", "");
                if (newPath.equals("")) {
                    newPath = getVarPath() + "\r\n";
                    channel.write(ByteBuffer.wrap(newPath.getBytes(StandardCharsets.UTF_8)));
                } else {
                    if (Files.isDirectory(Path.of(newPath(newPath).toString()), LinkOption.NOFOLLOW_LINKS)) {
                        Path directory = Paths.get(newPath(newPath).toString());
                        setVarPath(directory.toString());
                        String crPath = getVarPath() + "\r\n";
                        channel.write(ByteBuffer.wrap(crPath.getBytes(StandardCharsets.UTF_8)));
                    } else if (newPath.equals("/")){
                        setVarPath(path);
                        String crPath = path + "\r\n";
                        channel.write(ByteBuffer.wrap(crPath.getBytes(StandardCharsets.UTF_8)));
                    } else {
                        String errorDirectory = newPath + " is not directory" + "\r\n";
                        channel.write(ByteBuffer.wrap(errorDirectory.getBytes(StandardCharsets.UTF_8)));
                    }
                }
            } else if (message.startsWith("touch ")) {
                String fileName = message.replace("touch ", "");
                Files.createFile(Path.of(varPath + "\\" + fileName));
            } else if (message.equals("ls")) {
                String info = Files.list(Path.of(varPath))
                        .map(p -> p.getFileName().toString())
                        .collect(Collectors.joining(", "));
                info += "\r\n";
                channel.write(ByteBuffer.wrap(info.getBytes(StandardCharsets.UTF_8)));
            } else if (message.startsWith("mkdir ")) {
                String dirName = message.split(" +")[1];
                if (Files.notExists(Path.of(varPath, dirName))) {
                    Files.createDirectory(Path.of(varPath, dirName));
                }
            } else {
                channel.write(ByteBuffer.wrap("Unknown command!\r\n".getBytes(StandardCharsets.UTF_8)));
            }
        }
    }

    private void processAccept() throws IOException {
        SocketChannel channel = serverChannel.accept();
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_READ);
        buffer.put("Welcome to MikeOS. Show commands with --list\r\n".getBytes(StandardCharsets.UTF_8));
        buffer.rewind();
        channel.write(buffer);
        buffer.clear();
    }

    public String getPath() {
        return path;
    }

    public String getVarPath() {
        return varPath;
    }

    public void setVarPath(String varPath) {
        this.varPath = varPath;
    }

    private Path newPath(String path){
        Path newPath = Path.of(getPath() + "\\" + path);
        return newPath;
    }
}

