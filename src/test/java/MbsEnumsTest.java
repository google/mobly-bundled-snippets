import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Build.VERSION_CODES;
import com.google.android.mobly.snippet.bundled.utils.MbsEnums;
import com.google.common.truth.Truth;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.runner.AndroidJUnitRunner;
import org.junit.runners.JUnit4;
import org.robolectric.annotation.Config;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
@Config(minSdk = 33)
public class MbsEnumsTest {
  @Test
  public void testGetIntBitwiseOrValid() throws Throwable {
    Truth.assertThat(MbsEnums.BLE_PROPERTY_TYPE.getIntBitwiseOr("PROPERTY_READ|PROPERTY_NOTIFY")).isEqualTo(BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY);
    Truth.assertThat(MbsEnums.BLE_PROPERTY_TYPE.getIntBitwiseOr("PROPERTY_READ")).isEqualTo(BluetoothGattCharacteristic.PROPERTY_READ);
  }

  @Test
  public void testGetIntBitwiseOrInvalid() throws Throwable {
    Throwable thrown = null;
    try {
      MbsEnums.BLE_PROPERTY_TYPE.getIntBitwiseOr("PROPERTY_NOTHING");
    } catch (Throwable t) {
      thrown = t;
    }
    Truth.assertThat(thrown).isInstanceOf(NoSuchFieldError.class);
  }

  @Test
  public void testGetIntBitwiseOrInvalid2() throws Throwable {
    Throwable thrown = null;
    try {
      MbsEnums.BLE_PROPERTY_TYPE.getIntBitwiseOr("PROPERTY_READ|PROPERTY_NOTHING");
    } catch (Throwable t) {
      thrown = t;
    }
    Truth.assertThat(thrown).isInstanceOf(NoSuchFieldError.class);
  }
}
