package android.hardware.automotive.vehicle.V2_0;

import java.util.ArrayList;

/** Compile-time signature only; the head unit supplies the real HIDL class. */
public interface IVehicleCallback {
    void onPropertyEvent(ArrayList<VehiclePropValue> values);

    void onPropertySet(VehiclePropValue value);

    void onPropertySetError(int errorCode, int propertyId, int areaId);

    abstract class Stub implements IVehicleCallback {
    }
}
