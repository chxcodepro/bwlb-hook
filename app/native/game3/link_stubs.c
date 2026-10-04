/* Link-time ABI stubs. Only libcoinlock.so is packaged; Android supplies these libraries. */
#ifdef STUB_LIBC
int mprotect(void *address, unsigned long length, int protection) {
    (void)address; (void)length; (void)protection; return 0;
}
int getpagesize(void) { return 4096; }
#else
int dl_iterate_phdr(void *callback, void *data) { (void)callback; (void)data; return 0; }
#endif
