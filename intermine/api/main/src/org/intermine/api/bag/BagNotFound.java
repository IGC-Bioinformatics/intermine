package org.intermine.api.bag;

public class BagNotFound extends Exception {

    public BagNotFound(String bagName) {
        super("Unknown list: " + bagName);
    }
}
