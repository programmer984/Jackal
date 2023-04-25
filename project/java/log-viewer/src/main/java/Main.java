import org.example.endpoint.PacketReference;
import org.example.packets.LogFilePacketMarker;
import org.example.packets.PacketTypes;
import org.example.packetsReceiver.CommunicationDriver;
import org.example.packetsReceiver.OnePacketConsumer;
import org.example.packetsReceiver.PacketsReceiverStreamCollector;
import org.example.protocol.InternalProtocolHandler;
import org.example.softTimer.TimersManager;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * during communication you may log all requests (using log_packets=true) config in app.properties file
 * this program is validating 1.bin file and describe it content
 */
public class Main {
    public static final int MAX_PACKET_SIZE = 0xFFFF;
    public static final String NewLine = "\r\n";

    public static void main(String[] args) throws Exception {
        File binaryLogFile = new File(args[0]);
        if (!binaryLogFile.exists()) {
            throw new Exception("File does not exists" + args[0]);
        }
        File logFile = new File(binaryLogFile.getAbsolutePath() + ".log");
        LogWriter logWriter = null;

        try (FileInputStream fileInputStream = new FileInputStream(binaryLogFile);
             FileOutputStream logFileOutputStream = new FileOutputStream(logFile)) {
            logWriter = new LogWriter(logFileOutputStream);

            InternalProtocolHandler internalProtocolHandler = new InternalProtocolHandler();
            CommunicationDriver fsAccessDriver = () -> 100;
            TimersManager timersManager = new TimersManager();
            PacketsReceiverStreamCollector packetsReceiverStreamCollector =
                    new PacketsReceiverStreamCollector(timersManager, internalProtocolHandler,
                            fsAccessDriver, logWriter, MAX_PACKET_SIZE, 10000);
            int offset = 0;
            final int bufSize = 1000;
            final byte[] tmpBuf = new byte[bufSize];
            while (true) {
                int read = fileInputStream.read(tmpBuf, 0, bufSize);
                if (read<=0){
                    break;
                }
                logWriter.setInFileOffset(offset);
                packetsReceiverStreamCollector.onNewDataReceived(tmpBuf, 0, read);
                offset += read;
            }
        }catch(Exception ex){
            ex.printStackTrace();
            ex.printStackTrace(new PrintWriter(logWriter));
        }
    }

    private static class LogWriter extends Writer implements OnePacketConsumer {

        private final FileOutputStream logFileOutputStream;

        private int inFileOffset;

        private LogWriter(FileOutputStream logFileOutputStream) {
            this.logFileOutputStream = logFileOutputStream;
        }

        public void setInFileOffset(int inFileOffset) {
            this.inFileOffset = inFileOffset;
        }

        @Override
        public void accept(byte[] data, int offset, int size, Integer logId) {
            PacketReference packetReference = new PacketReference(data, offset, size);
            String logText;
            if (packetReference.getPacketType().equals(PacketTypes.LogFilePacketMarker)) {
                logText = LogFilePacketMarker.getText(data, offset);
            }else{
                logText = "Description was not calculated";
            }
            String line = String.format("%04x %s\t%s\r\n", inFileOffset + offset, packetReference.getPacketType(), logText);
            try {
                logFileOutputStream.write(line.getBytes(StandardCharsets.US_ASCII));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            for (int i=off;i<len;i++){
                logFileOutputStream.write(cbuf[off+i]);
            }
        }

        @Override
        public void flush() throws IOException {

        }

        @Override
        public void close() throws IOException {

        }
    }

}
