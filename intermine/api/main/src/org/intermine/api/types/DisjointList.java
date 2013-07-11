package org.intermine.api.types;

import java.util.ArrayList;

import org.intermine.api.types.Either.Left;
import org.intermine.api.types.Either.Right;

public class DisjointList<L, R> extends ArrayList<Either<L, R>> {
    public boolean addLeft(L lefty) {
        super.add(new Left<L, R>(lefty));
        return true;
    }
    public boolean addRight(R righty) {
        super.add(new Right<L, R>(righty));
        return true;
    }
}