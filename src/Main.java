import java.util.Collection;

public class Main {

    public static void main(String[] args) throws Exception {

        // There are two ways to connect to a Bulb:

        // 1 - automatically discovers every bulbs on the network, see doc to get more info
        Collection<Bulb> bulbs = Bulb.discover(10 * 1000);

        // 2 - specify Bulb ip to connect to
        Bulb bulb = new Bulb("192.168.1.94");

        // turns bulb on
        bulb.turnOn();

        // turns bulb off
        bulb.turnOff();

        // sets bulb's brightness to 100 (max)
        bulb.setBrightness(100);

        /* you can specify the method (and params) you want to run on the bulb
         * this API does not implement every available methods
         * (see Yeelight developer doc to know about available methods)
         * so this method is useful because it let's you run any method. */
        String method = "set_power";
        Object[] params = new Object[]{"on", "smooth", 500};
        bulb.runMethod(method, params);
    }
}
