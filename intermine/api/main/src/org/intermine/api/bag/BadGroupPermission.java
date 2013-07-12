package org.intermine.api.bag;

public class BadGroupPermission extends Exception {

    public BadGroupPermission(String user, String group) {
        super(String.format("This user (%s) does not belong to this group (%s)", user, group));
    }
}
