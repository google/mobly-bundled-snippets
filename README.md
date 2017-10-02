Mobly Bundled Snippets is a set of Snippets to allow Mobly tests to control
Android devices by exposing a simplified verison of the public Android API
suitable for testing.

We are adding more APIs as we go. If you have specific needs for certain groups
of APIs, feel free to file a request in [Issues](https://github.com/google/mobly-bundled-snippets/issues).

Note: this is not an official Google product.


## Usage

1.  Compile and install the bundled snippets

        ./gradlew assembleDebug
        adb install -d -r -g ./build/outputs/apk/mobly-bundled-snippets-debug.apk

1.  Use the Mobly snippet shell to interact with the bundled snippets

        snippet_shell.py com.google.android.mobly.snippet.bundled
        >>> print(s.help())
        Known methods:
          bluetoothDisable() returns void  // Disable bluetooth with a 30s timeout.
        ...
          wifiDisable() returns void  // Turns off Wi-Fi with a 30s timeout.
          wifiEnable() returns void  // Turns on Wi-Fi with a 30s timeout.
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

## Develop

If you want to contribute, use the usual github method of forking and sending
a pull request.

Before asking for a pull request, run the `presubmit` target to format and run
lint over the code. Fix any issues it indidates. When complete, ask for the
pull request.

```sh
./gradlew presubmit
```


## Other resources

  * [Mobly multi-device test framework](http://github.com/google/mobly)
  * [Mobly Snippet Lib](http://github.com/google/mobly-snippet-lib)
