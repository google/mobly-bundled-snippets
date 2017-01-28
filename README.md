Mobly Bundled Snippets is a set of Snippets to allow Mobly tests to control
Android devices.

They expose a simplified verison of the public Android API suitable for testing.

Note: this is not an official Google product.


## Usage

1.  Compile and install the bundled snippets

        ./gradlew assembleDebug
        adb install -d -r -r ./build/outputs/apk/mobly-bundled-snippets-debug.apk

1.  Use the Mobly snippet shell to interact with the bundled snippets

        snippet_shell.py com.google.android.mobly.snippet.bundled
        >>> print(s.help())
        Known methods:
          bluetoothDisable() returns void  // Enable bluetooth
        ...

1.  To use these snippets within Mobly tests, load it on your AndroidDevice objects
    after registering android_device module:

    ```python
    def setup_class(self):
      self.ad = self.register_controllers(android_device, min_number=1)[0]
      self.ad.load_snippet('api', 'com.google.android.mobly.snippet.bundled')

    def test_enable_wifi(self):
      self.ad.api.wifiEnable()
    ```


## Other resources

  * [Mobly multi-device test framework](http://github.com/google/mobly)
  * [Mobly Snippet Lib](http://github.com/google/mobly-snippet-lib)
