# Installation - Known Issues

If you encounter a message stating "Package is damaged" when double-clicking `aibtra`, you need to reset the quarantine attributes by using the following command:

```
sudo xattr -rd com.apple.quarantine <path/to/aibtra>
```
