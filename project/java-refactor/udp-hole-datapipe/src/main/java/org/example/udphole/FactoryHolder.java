package org.example.udphole;


public class FactoryHolder {
    public static BeansFactory getFactory() {
        return factory;
    }

    public static void setFactory(BeansFactory factory) {
        FactoryHolder.factory = factory;
    }

    private static BeansFactory factory;

}
