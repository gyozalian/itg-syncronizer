package com.itg;

import java.nio.file.Path;
import java.util.logging.Logger;

public class Test {
    static Logger logger = Logger.getLogger("test");

    public static void main(String[] args) {
        PrintUtils.printFile("HP LaserJet M14-M17", Path.of("D:\\swift\\DelMsg\\Tigran.xml"),logger);
    }
}
