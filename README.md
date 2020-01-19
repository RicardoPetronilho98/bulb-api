# bulb-api
Java API to connect and control Xiaomi Yeelight Bulb.

### Example of usage ###
```java
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
```

### Requeriments ###
1. You need to enable <mark>LAN control</mark> inorder to allow 3rd party API's (like this one) to access and controll your device. See [this site](https://www.yeelight.com/faqs/lan_control) to set it up.

2. As already said in example of usage, this API does not implement every available methods, however there is an <mark>easy and elegant</mark> way to simplify method invocation. Using <mark>bulb.runMethod()</mark> you just need to specify the method using String and its parameteres using Object Array, internally it encapsulates  command conversion to Yeelight's protocol and TCP socket writting. See [official yeelight doc](https://www.yeelight.com/download/Yeelight_Inter-Operation_Spec.pdf) to know all available methods.

3. This API is directed to Bulb device however it works with all Yeelight devices, you can use bulb.runMethod() to invoce a specific method not implemented in this API. Note that at this moment Bulb.discover() method is Bulb specific. 

### Notes ###
This software is released into the public domain under [The Unlicense](https://github.com/RicardoPetronilho98/bulb-api/blob/master/LICENSE). 