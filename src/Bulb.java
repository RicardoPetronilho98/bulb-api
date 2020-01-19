import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.*;

public class Bulb implements Comparable<Bulb> {

    // static variables needed to discover bulbs on multi-cast address
    private static final String MULTICAST_IP = "239.255.255.250";
    private static final int MULTICAST_PORT = 1982;
    private static final String MULTICAST_ADDRESS = MULTICAST_IP + ":" + MULTICAST_PORT;
    private static final String UDP_REQUEST =
            "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: " + MULTICAST_ADDRESS + "\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "ST: wifi_bulb";
    private static final int BUFF_SIZE = 1024; // 1024 B = 1 KB

    // default port used to connect to bulb over TCP
    private static final int DEFAULT_TCP_PORT = 55443;

    // time (in ms) between each status advertisement
    private int refreshRate;

    // TCP address
    private String ip;
    private int port;

    private String id;

    // mono - "normal" lamp can only adjust brightness
    // color - RGB lamp
    // ... - there are many types of lamps (see documentation for more info)
    private String model;

    private int firmwareVersion;

    // available methods for 3rd party control
    private List<String> methods;

    // true - on
    // false - off
    private boolean power;

    // 0 - 100
    private int brightness;

    // Current light mode.
    // 1 - color mode
    // 2 - color temperature mode
    // 3 - flow mode
    private int colorMode;

    // only valid if colorMode == 2
    private int colorTemperature;

    private int rgb;

    // only valid if colorMode == 3
    private int hue;
    private int saturation;

    // name given by user
    // max 64 bytes with only ASCII characters
    private String name;

    // used for TCP control connection
    private Socket s;
    private PrintWriter pw;
    private BufferedReader br;
    private int methodId;

    private Bulb() {}

    /**
     * Creates a Bulb object used to connect to and control a Xiaomi (Yeelight) Light Bulb.
     * @param ip ip address of light bulb.
     * @throws IOException if TCP connection to light bulb can not be established.
     */
    public Bulb(String ip) throws IOException {
        this.ip = ip;
        this.port = DEFAULT_TCP_PORT;
        this.methodId = 0;
        this.initTCP();
    }


    /**
     * Every bulb listens on multi-cast address waiting for any incoming search requests.
     * This method sends an UDP request to that address and if any bulb receives the request
     * it will uni-cast an UDP response to the searcher.
     * Information on the UDP reply is parsed to Bulb class.
     * At last TCP connection is configured in order to later send TCP control packets.
     * @param timeout amount of time to wait for any UDP response on multi-cast address.
     * @return every bulb discovered on the network.
     * @throws IOException if UDP or TCP connection can not be established.
     */
    public static Collection<Bulb> discover(int timeout) throws IOException {

        /** creates UDP packet to hold request. */
        InetAddress IPAddress = InetAddress.getByName(MULTICAST_IP);
        byte[] sendData = UDP_REQUEST.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, MULTICAST_PORT);

        /** sends request to multi-cast address. */
        DatagramSocket socket = new DatagramSocket(); // creates socket used for UDP connection
        socket.setSoTimeout(timeout);
        socket.send(sendPacket);
        System.out.println("discover(): sent UDP request to multi-cast channel on " + MULTICAST_ADDRESS + " with timeout of " + timeout + " ms.");

        /** creates UDP packet to hold reply. */
        byte[] receiveData = new byte[BUFF_SIZE];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

        /** Collection containing all bulbs discovered on the network.
         * Note: used Set here to prevent having repeated bulbs in case the same bulb replies more than once.
         * In order to use Set this class implements Comparable interface. */
        Set<Bulb> bulbs = new TreeSet<>();

        try {
            while (true) { /** tries to receive UDP replies till timeout is reached. */

                /** receives UDP packet reply from a light bulb. */
                socket.receive(receivePacket);
                System.out.println("discover(): received UDP reply from " + receivePacket.getAddress().toString());

                /** parses received data to Bulb class. */
                Bulb bulb = Bulb.parse(new String(receivePacket.getData()));

                /** initiate and configure TCP connection. */
                bulb.initTCP();

                bulbs.add(bulb);
            }
        } catch (IOException e) {
            // e.printStackTrace();
            System.err.println("discover(): timeout (" + timeout + " ms) reached, assuming there are no more bulbs on the network.");
        }

        socket.close(); // closes socket used for UDP connection
        System.out.println("discover(): discovered the following bulbs:");
        bulbs.forEach(System.out::println);
        return bulbs;
    }


    /**
     * Configures TCP socket creating an output steam for sending data and input stream for receiving data.
     * @throws IOException if TCP connection can not be established.
     */
    private void initTCP() throws IOException {
        this.s = new Socket(ip, port);
        this.pw = new PrintWriter(s.getOutputStream(), true);
        this.br = new BufferedReader( new InputStreamReader(s.getInputStream()) );
    }


    /**
     * Parses all useful headers on UDP packet to Bulb class.
     * @param data data from UDP packet in String format.
     * @return return Bulb class holding all UDP packet information.
     */
    private static Bulb parse(String data) {

        Bulb bulb = new Bulb();

        Scanner sc = new Scanner(data);
        sc.nextLine(); // skip first header (don't need it)

        String header2 = sc.nextLine();
        bulb.refreshRate = Integer.parseInt( header2.substring(header2.indexOf("=")+1) );

        sc.nextLine(); // skip third header (don't need it)
        sc.nextLine(); // skip fourth header (don't need it)

        String header5 = sc.nextLine();
        bulb.ip = header5.substring(header5.lastIndexOf("/")+1, header5.lastIndexOf(":"));
        bulb.port = Integer.parseInt(header5.substring(header5.lastIndexOf(":")+1));

        sc.nextLine(); // skip sixth header (don't need it)

        String header7 = sc.nextLine();
        bulb.id = header7.substring(header7.lastIndexOf(" ")+1);

        String header8 = sc.nextLine();
        bulb.model = header8.substring(header8.lastIndexOf(" ")+1);

        String header9 = sc.nextLine();
        bulb.firmwareVersion = Integer.parseInt(header9.substring(header9.lastIndexOf(" ")+1));

        String header10 = sc.nextLine();
        String[] methods = header10.substring(header10.indexOf(" ")+1).split(" ");
        bulb.methods = new ArrayList<>();
        bulb.methods.addAll(Arrays.asList(methods));

        String header11 = sc.nextLine();
        if (header11.substring(header11.lastIndexOf(" ")+1).equals("on")) bulb.power = true;
        else bulb.power = false;

        String header12 = sc.nextLine();
        bulb.brightness = Integer.parseInt(header12.substring(header12.lastIndexOf(" ")+1));

        String header13 = sc.nextLine();
        bulb.colorMode = Integer.parseInt(header13.substring(header13.lastIndexOf(" ")+1));

        String header14 = sc.nextLine();
        bulb.colorTemperature = Integer.parseInt(header14.substring(header14.lastIndexOf(" ")+1));

        String header15 = sc.nextLine();
        bulb.rgb = Integer.parseInt(header15.substring(header15.lastIndexOf(" ")+1));

        String header16 = sc.nextLine();
        bulb.hue = Integer.parseInt(header16.substring(header16.lastIndexOf(" ")+1));

        String header17 = sc.nextLine();
        bulb.saturation = Integer.parseInt(header17.substring(header17.lastIndexOf(" ")+1));

        String header18 = sc.nextLine();
        bulb.name = header18.substring(header18.lastIndexOf(" ")+1);

        bulb.methodId = 0;
        return bulb;
    }


    /**
     * Converts this Bulb to String format.
     * @return this bulb in String format.
     */
    @Override
    public String toString() {
        return "Bulb{" +
                "refreshRate=" + refreshRate +
                ", ip='" + ip + '\'' +
                ", port=" + port +
                ", id='" + id + '\'' +
                ", model='" + model + '\'' +
                ", firmwareVersion=" + firmwareVersion +
                ", methods=" + methods +
                ", power=" + power +
                ", brightness=" + brightness +
                ", colorMode=" + colorMode +
                ", colorTemperature=" + colorTemperature +
                ", rgb=" + rgb +
                ", hue=" + hue +
                ", saturation=" + saturation +
                ", name='" + name + '\'' +
                '}';
    }


    /**
     * Natural comparison (by id) of this bulb to another.
     * @param bulb other bulb.
     * @return comparison in int format (-1, 0 or 1)
     */
    @Override
    public int compareTo(Bulb bulb) {
        // took advantage of string.compareTo(string) method instead of implementing my own
        return this.id.compareTo(bulb.id);
    }

    
    /**
     * Generates the JSON format command holding method and parameters.
     * After sends it to Bulb socket over TCP.
     * @param method method that will be applied to Bulb.
     * @param params method's parameters.
     */
    public void runMethod(String method, Object[] params) {
        // used StringBuilder to generate JSON format command efficiently.
        StringBuilder sb = new StringBuilder();
        sb.append("{\"id\":").append(methodId).append(",\"method\":\"").append(method).append("\",\"params\":[");
        for(Object o: params) {
            // based on parameter type the right format is generated.
            if (o instanceof String) sb.append("\"").append(o).append("\"");
            else if (o instanceof Integer) sb.append(o);
            sb.append(",");
        }
        sb.deleteCharAt(sb.length()-1); // removes the last comma -> ','
        sb.append("]}\r\n");
        methodId++; // increments method id
        pw.println(sb.toString()); // writes command to bulb's socket
        pw.flush(); // makes sure command is written
    }


    /** Turns off bulb with default duration of 500 ms. */
    public void turnOff() {
        turnOff(500);
    }


    /**
     * Turns off bulb with given duration.
     * @param duration duration to smoothly turn bulb off.
     */
    public void turnOff(int duration) {
        runMethod("set_power", new Object[]{"off", "smooth", duration});
        this.power = false;
        System.out.println("turnOff(): turning off bulb " + ip);
    }


    /** Turns on bulb with default duration of 500 ms. */
    public void turnOn() {
        turnOn(500);
    }


    /**
     * Turns on bulb with given duration.
     * @param duration duration to smoothly turn bulb oo.
     */
    public void turnOn(int duration) {
        runMethod("set_power", new Object[]{"on", "smooth", duration});
        this.power = true;
        System.out.println("turnOn(): turning on bulb " + ip);
    }


    /**
     * Sets bulb's brightness ranged between 1 and 100.
     * @param brightness bulb's brightness ranged between 1 and 100.
     */
    public void setBrightness(int brightness) {
        runMethod("set_bright", new Object[]{brightness, "smooth", 500});
        this.brightness = brightness;
        System.out.println("setBrightness(): setting brightness to " + brightness + " on bulb " + ip);
    }

}