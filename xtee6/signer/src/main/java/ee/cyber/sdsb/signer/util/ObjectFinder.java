package ee.cyber.sdsb.signer.util;

import iaik.pkcs.pkcs11.Session;
import iaik.pkcs.pkcs11.TokenException;

import java.util.ArrayList;
import java.util.List;

public class ObjectFinder {

    @SuppressWarnings("unchecked")
    public static <T extends iaik.pkcs.pkcs11.objects.Object> List<T> find(
            T template, Session session, int maxObjectCount)
                    throws TokenException {
        iaik.pkcs.pkcs11.objects.Object[] tmpArray;

        List<T> foundObjects = new ArrayList<>();

        session.findObjectsInit(template);
        do {
            tmpArray = session.findObjects(maxObjectCount);
            for (iaik.pkcs.pkcs11.objects.Object object: tmpArray) {
                foundObjects.add((T) object);
            }
        } while (tmpArray.length != 0);

        session.findObjectsFinal();
        return foundObjects;
    }

    @SuppressWarnings("unchecked")
    public static <T extends iaik.pkcs.pkcs11.objects.Object> T find(
            T template, Session session) throws TokenException {
        T foundObject = null;

        session.findObjectsInit(template);

        iaik.pkcs.pkcs11.objects.Object[] tmpArray = session.findObjects(1);
        if (tmpArray.length > 0) {
            foundObject = (T) tmpArray[0];
        }

        session.findObjectsFinal();
        return foundObject;
    }
}
