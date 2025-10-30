FREESWITCH_MOD_PATH=/usr/local/freeswitch/mod
FREESWITCH_INCLUDE=/usr/local/freeswitch/include/freeswitch

MODNAME = mod_easymrcp_spy.so
MODOBJ = mod_easymrcp_spy.o
MODCFLAGS = -Wall -I$(FREESWITCH_INCLUDE)

CC = gcc
CFLAGS = -fPIC -g -ggdb $(MODCFLAGS)
LDFLAGS = -shared

.PHONY: all clean install

all: $(MODNAME)

$(MODNAME): $(MODOBJ)
	$(CC) $(LDFLAGS) -o $@ $(MODOBJ)

%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

clean:
	rm -f $(MODNAME) $(MODOBJ)

install: $(MODNAME)
	install -d $(FREESWITCH_MOD_PATH)
	install $(MODNAME) $(FREESWITCH_MOD_PATH)

.PHONY: help
help:
	@echo "Usage:"
	@echo "  make          - 编译模块"
	@echo "  make clean    - 清理编译文件"
	@echo "  make install  - 安装模块到FreeSWITCH"
	@echo ""
	@echo "配置说明："
	@echo "  FREESWITCH_MOD_PATH    - FreeSWITCH模块安装路径"
	@echo "  FREESWITCH_INCLUDE     - FreeSWITCH头文件路径" 