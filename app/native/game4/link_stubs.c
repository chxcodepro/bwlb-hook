#ifdef STUB_LIBC
int getpagesize(void) { return 4096; }
#else
int dl_iterate_phdr(void *callback, void *data) { return 0; }
#endif
