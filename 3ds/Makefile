#---------------------------------------------------------------------------------
# kai's master tool, for the Nintendo 3DS.
#
# Two outputs, and the difference between them matters:
#
#   make            -> kai-master-tool.3dsx   (Homebrew Launcher, permissive)
#   make cia        -> kai-master-tool.cia    (installed title, app.rsf's rules)
#
# The .3dsx is for iterating. The .cia is what actually ships, and it is the one
# that will find a service missing from app.rsf. Test the .cia before believing
# a feature works - see the note at the top of app.rsf.
#
# The host-side conformance suite is a separate build with a separate compiler:
#   make -C test test
# It needs no devkitARM at all, which is why it can run on every push.
#---------------------------------------------------------------------------------
.SUFFIXES:

ifeq ($(strip $(DEVKITARM)),)
$(error "DEVKITARM is not set. Install devkitPro and source its environment, or \
build in the devkitpro/devkitarm container - see .github/workflows/build-3ds.yml")
endif

TOPDIR ?= $(CURDIR)
include $(DEVKITARM)/3ds_rules

#---------------------------------------------------------------------------------
# metadata baked into the SMDH, which is what the HOME menu reads
#---------------------------------------------------------------------------------
APP_TITLE       := kai's master tool
APP_DESCRIPTION := Yu-Gi-Oh! deck building and play
APP_AUTHOR      := kaiharimoto

VERSION         := $(shell cat $(TOPDIR)/VERSION)
VER_MAJOR       := $(word 1,$(subst ., ,$(VERSION)))
VER_MINOR       := $(word 2,$(subst ., ,$(VERSION)))
VER_MICRO       := $(word 3,$(subst ., ,$(VERSION)))

#---------------------------------------------------------------------------------
TARGET          := kai-master-tool
BUILD           := build
SOURCES         := src src/core src/gfx src/ar src/ui src/net
INCLUDES        := src src/core
GRAPHICS        :=
ROMFS           := romfs
SHADERS         := shaders

ICON            := $(TOPDIR)/meta/icon.png
BANNER_IMAGE    := $(TOPDIR)/meta/banner.png
BANNER_AUDIO    := $(TOPDIR)/meta/banner.wav
RSF             := $(TOPDIR)/app.rsf

#---------------------------------------------------------------------------------
# code generation options
#---------------------------------------------------------------------------------
ARCH    := -march=armv6k -mtune=mpcore -mfloat-abi=hard -mtp=soft

CFLAGS  := -g -Wall -Wextra -O2 -mword-relocations \
           -ffunction-sections -fdata-sections \
           $(ARCH) $(INCLUDE) -D__3DS__ -DMT_VERSION=\"$(VERSION)\"
CFLAGS  += -std=gnu99

CXXFLAGS := $(CFLAGS) -fno-rtti -fno-exceptions -std=gnu++17
ASFLAGS  := -g $(ARCH)
LDFLAGS   = -specs=3dsx.specs -g $(ARCH) -Wl,-Map,$(notdir $*.map) -Wl,--gc-sections

# citro2d before citro3d before ctru: the linker resolves left to right and
# each of these depends on the next.
LIBS    := -lcitro2d -lcitro3d -lctru -lm
LIBDIRS := $(CTRULIB)

#---------------------------------------------------------------------------------
ifneq ($(BUILD),$(notdir $(CURDIR)))
#---------------------------------------------------------------------------------
export OUTPUT   := $(CURDIR)/$(TARGET)
export TOPDIR   := $(CURDIR)

# SHADERS belongs here too: the sub-make runs in build/ and finds every
# prerequisite through VPATH, so a shader directory left out of it is a
# .v.pica make cannot see and a "no rule to make target" on the generated
# header rather than on the file that is actually missing.
export VPATH    := $(foreach dir,$(SOURCES),$(CURDIR)/$(dir)) \
                   $(foreach dir,$(GRAPHICS),$(CURDIR)/$(dir)) \
                   $(foreach dir,$(SHADERS),$(CURDIR)/$(dir))

export DEPSDIR  := $(CURDIR)/$(BUILD)

CFILES          := $(foreach dir,$(SOURCES),$(notdir $(wildcard $(dir)/*.c)))
CPPFILES        := $(foreach dir,$(SOURCES),$(notdir $(wildcard $(dir)/*.cpp)))
SFILES          := $(foreach dir,$(SOURCES),$(notdir $(wildcard $(dir)/*.s)))
PICAFILES       := $(foreach dir,$(SHADERS),$(notdir $(wildcard $(dir)/*.v.pica)))
SHLISTFILES     := $(foreach dir,$(SHADERS),$(notdir $(wildcard $(dir)/*.shlist)))
GFXFILES        := $(foreach dir,$(GRAPHICS),$(notdir $(wildcard $(dir)/*.t3s)))

export LD       := $(if $(strip $(CPPFILES)),$(CXX),$(CC))

export OFILES_SOURCES := $(CPPFILES:.cpp=.o) $(CFILES:.c=.o) $(SFILES:.s=.o)
export OFILES_BIN     := $(addsuffix .o,$(PICAFILES:.v.pica=.shbin)) \
                         $(addsuffix .o,$(SHLISTFILES:.shlist=.shbin))
export OFILES         := $(OFILES_BIN) $(OFILES_SOURCES)
export HFILES         := $(addsuffix .h,$(subst .,_,$(PICAFILES:.v.pica=.shbin))) \
                         $(addsuffix .h,$(subst .,_,$(SHLISTFILES:.shlist=.shbin)))

export INCLUDE  := $(foreach dir,$(INCLUDES),-I$(CURDIR)/$(dir)) \
                   $(foreach dir,$(LIBDIRS),-I$(dir)/include) \
                   -I$(CURDIR)/$(BUILD)

export LIBPATHS := $(foreach dir,$(LIBDIRS),-L$(dir)/lib)

ifneq ($(strip $(ROMFS)),)
  ifneq ($(wildcard $(CURDIR)/$(ROMFS)/.),)
    export ROMFS_DIR := $(CURDIR)/$(ROMFS)
  endif
endif

.PHONY: all cia clean test

all: $(BUILD)
	@$(MAKE) --no-print-directory -C $(BUILD) -f $(CURDIR)/Makefile $(OUTPUT).3dsx

cia: $(BUILD)
	@$(MAKE) --no-print-directory -C $(BUILD) -f $(CURDIR)/Makefile $(OUTPUT).cia

# The conformance suite. Host compiler, no devkitARM, no console.
test:
	@$(MAKE) --no-print-directory -C test test

$(BUILD):
	@mkdir -p $@

clean:
	@echo clean ...
	@rm -fr $(BUILD) $(TARGET).3dsx $(TARGET).cia $(TARGET).smdh \
	        $(TARGET).elf $(TARGET).bnr $(TARGET).icn
	@$(MAKE) --no-print-directory -C test clean

#---------------------------------------------------------------------------------
else
#---------------------------------------------------------------------------------
DEPENDS := $(OFILES:.o=.d)

# picasso and bin2s turn shaders/*.v.pica into a generated header, and main.c
# includes it. Without this every source file races the shader build and the
# first clean build fails on a missing scene_shbin.h.
$(OFILES_SOURCES) : $(HFILES)

# Assigned, not appended, and that is the whole point.
#
# This Makefile includes 3ds_rules at the top, so it is included *twice*: once
# at the top level and once by the sub-make running inside build/. Anything
# 3ds_rules appends to _3DSXFLAGS in terms of $(CURDIR) therefore comes out
# pointing at build/ the second time - which is why 3dsxtool reported "Cannot
# open SMDH file!" for a file that had just been built one directory up. A ':='
# here discards whatever accumulated and states the absolute paths once.
_3DSXFLAGS := --smdh=$(OUTPUT).smdh
ifneq ($(strip $(ROMFS_DIR)),)
_3DSXFLAGS += --romfs=$(ROMFS_DIR)
endif

$(OUTPUT).3dsx : $(OUTPUT).elf $(OUTPUT).smdh
$(OUTPUT).elf  : $(OFILES)

$(OUTPUT).smdh : $(ICON)
	@smdhtool --create "$(APP_TITLE)" "$(APP_DESCRIPTION)" "$(APP_AUTHOR)" $(ICON) $@
	@echo "built ... $(notdir $@)"

$(OUTPUT).bnr : $(BANNER_IMAGE) $(BANNER_AUDIO)
	@bannertool makebanner -i $(BANNER_IMAGE) -a $(BANNER_AUDIO) -o $@ >/dev/null
	@echo "built ... $(notdir $@)"

$(OUTPUT).icn : $(ICON)
	@bannertool makesmdh -s "$(APP_TITLE)" -l "$(APP_DESCRIPTION)" \
	    -p "$(APP_AUTHOR)" -i $(ICON) -o $@ >/dev/null
	@echo "built ... $(notdir $@)"

# -target t writes a title with the dev signature FBI will install. The version
# triple comes from VERSION and must only ever go up, or an install reads as a
# downgrade and is refused.
$(OUTPUT).cia : $(OUTPUT).elf $(OUTPUT).bnr $(OUTPUT).icn $(RSF)
	@makerom -f cia -target t -exefslogo \
	    -o $@ \
	    -elf $(OUTPUT).elf \
	    -rsf $(RSF) \
	    -banner $(OUTPUT).bnr \
	    -icon $(OUTPUT).icn \
	    -major $(VER_MAJOR) -minor $(VER_MINOR) -micro $(VER_MICRO) \
	    -DAPP_TITLE="$(APP_TITLE)" \
	    -DAPP_PRODUCT_CODE="CTR-P-KMTL" \
	    -DAPP_UNIQUE_ID="0xFF4D7" \
	    -DAPP_ROMFS="$(TOPDIR)/$(ROMFS)"
	@echo "built ... $(notdir $@)"

# The canonical pair, and not hand-rolled on purpose.
#
# 3ds_rules already knows how to turn a .v.pica into a .shbin with picasso, and
# base_tools (which it includes, via base_rules) defines bin2o - which runs
# bin2s with the right alignment, derives the header name from the input, and
# assembles through $(CC) rather than $(AS) so CPPFLAGS reach it. A local
# version of that has to get all four of those right and gains nothing.
%.shbin.o %_shbin.h : %.shbin
	$(SILENTMSG) $(notdir $<)
	$(bin2o)

-include $(DEPENDS)

#---------------------------------------------------------------------------------
endif
#---------------------------------------------------------------------------------
