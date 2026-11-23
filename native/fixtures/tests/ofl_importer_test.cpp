// Tests OFL mode import, channel mapping, and library metadata handling.

// Tests for the OFL importer.
// The example JSON texts below are small, hand-written fixtures modeled after the Open Fixture Library structure.
// They do not contain copied files and may therefore live in the repository.

#include <iostream>
#include <stdexcept>
#include <string>

#include "../ofl_importer.h"

using namespace archx;

// ---------- Small test helpers ----------

static int failures = 0;

#define CHECK(cond)                                                                  \
    do {                                                                             \
        if (!(cond)) {                                                               \
            std::cerr << "ERROR " << __FILE__ << ":" << __LINE__ << ": " #cond "\n"; \
            ++failures;                                                              \
        }                                                                            \
    } while (0)

#define CHECK_EQ(a, b)                                                               \
    do {                                                                             \
        auto va = (a);                                                               \
        auto vb = (b);                                                               \
        if (!(va == vb)) {                                                           \
            std::cerr << "ERROR " << __FILE__ << ":" << __LINE__ << ": " #a " == " #b \
                      << "  (" << va << " != " << vb << ")\n";                       \
            ++failures;                                                              \
        }                                                                            \
    } while (0)

// The stretch goals (fine channels, unused channels) can be enabled when you are ready for them.
constexpr bool kRunStretchTests = false;

// ---------- Sample data ----------

// A simple fixture: intensity, RGB, macro, strobe.
static const std::string kMiniTriPar = R"json({
  "name": "Platinum Mini TRI-PAR",
  "meta": { "lastModifyDate": "2018-09-22" },
  "availableChannels": {
    "Intensity": { "highlightValue": 255, "precedence": "HTP", "capability": { "type": "Intensity" } },
    "Red":   { "highlightValue": 255, "precedence": "HTP", "capability": { "type": "ColorIntensity", "color": "Red" } },
    "Green": { "highlightValue": 255, "precedence": "HTP", "capability": { "type": "ColorIntensity", "color": "Green" } },
    "Blue":  { "highlightValue": 255, "precedence": "HTP", "capability": { "type": "ColorIntensity", "color": "Blue" } },
    "Macro": {
      "capabilities": [
        { "dmxRange": [0, 4],   "type": "NoFunction" },
        { "dmxRange": [5, 9],   "type": "ColorPreset", "colors": ["#ffffff"] },
        { "dmxRange": [85, 169], "type": "Effect", "effectPreset": "ColorJump" }
      ]
    },
    "Strobe": {
      "highlightValue": 200,
      "capabilities": [
        { "dmxRange": [0, 4],   "type": "ShutterStrobe", "shutterEffect": "Open" },
        { "dmxRange": [5, 255], "type": "ShutterStrobe", "shutterEffect": "Strobe" }
      ]
    }
  },
  "modes": [
    { "name": "6-channel", "shortName": "6ch", "channels": ["Intensity", "Red", "Green", "Blue", "Macro", "Strobe"] }
  ],
  "fixtureKey": "platinum-mini-tri-par",
  "manufacturerKey": "lightmaxx"
})json";

// A small moving head with fine channels (16-bit) that do not have to be adjacent to their main channel.
static const std::string kMover = R"json({
  "name": "Test Mover",
  "meta": { "lastModifyDate": "2020-01-01" },
  "availableChannels": {
    "Pan":    { "fineChannelAliases": ["Pan fine"],  "capability": { "type": "Pan",  "angleStart": "0deg", "angleEnd": "540deg" } },
    "Tilt":   { "fineChannelAliases": ["Tilt fine"], "capability": { "type": "Tilt", "angleStart": "0deg", "angleEnd": "270deg" } },
    "Dimmer": { "capability": { "type": "Intensity" } }
  },
  "modes": [
    { "name": "5-channel", "channels": ["Pan", "Pan fine", "Tilt", "Tilt fine", "Dimmer"] }
  ],
  "fixtureKey": "test-mover",
  "manufacturerKey": "test"
})json";

// A mode with an unused channel (null).
static const std::string kUnused = R"json({
  "name": "Test Unused",
  "meta": { "lastModifyDate": "2021-02-02" },
  "availableChannels": {
    "Intensity": { "capability": { "type": "Intensity" } },
    "Red": { "capability": { "type": "ColorIntensity", "color": "Red" } }
  },
  "modes": [
    { "name": "3-channel", "channels": ["Intensity", null, "Red"] }
  ],
  "fixtureKey": "test-unused",
  "manufacturerKey": "test"
})json";

// ---------- Tests ----------

static void test_mini_tri_par_basics() {
    FixtureType type = ImportOflMode(kMiniTriPar, "lightmaXX", "6-channel");

    CHECK_EQ(type.name(), std::string("Platinum Mini TRI-PAR"));
    CHECK_EQ(type.manufacturer(), std::string("lightmaXX"));
    CHECK_EQ(type.mode_name(), std::string("6-channel"));
    CHECK_EQ(type.library_source(), std::string("ofl"));
    CHECK_EQ(type.library_key(), std::string("lightmaxx/platinum-mini-tri-par/6-channel"));
    CHECK_EQ(type.library_revision(), std::string("2018-09-22"));
    CHECK_EQ(type.channel_count(), 6u);
    CHECK_EQ(type.channels_size(), 6);
    CHECK_EQ(type.sub_fixtures_size(), 0);
}

static void test_mini_tri_par_channels() {
    FixtureType type = ImportOflMode(kMiniTriPar, "lightmaXX", "6-channel");
    if (type.channels_size() != 6) {
        std::cerr << "ERROR: expected 6 channels, got " << type.channels_size() << "\n";
        ++failures;
        return;
    }

    const ChannelDef &intensity = type.channels(0);
    CHECK_EQ(intensity.offset(), 1u);
    CHECK_EQ(intensity.attribute(), ATTRIBUTE_INTENSITY);
    CHECK_EQ(intensity.name(), std::string("Intensity"));
    CHECK_EQ(intensity.highlight_value(), 255u);
    CHECK_EQ(intensity.precedence(), PRECEDENCE_HTP);

    CHECK_EQ(type.channels(1).attribute(), ATTRIBUTE_RED);
    CHECK_EQ(type.channels(1).offset(), 2u);
    CHECK_EQ(type.channels(2).attribute(), ATTRIBUTE_GREEN);
    CHECK_EQ(type.channels(2).offset(), 3u);
    CHECK_EQ(type.channels(3).attribute(), ATTRIBUTE_BLUE);
    CHECK_EQ(type.channels(3).offset(), 4u);

    // The macro contains only presets and effects, so we treat it as EFFECT.
    CHECK_EQ(type.channels(4).attribute(), ATTRIBUTE_EFFECT);
    CHECK_EQ(type.channels(4).offset(), 5u);
    CHECK_EQ(type.channels(4).precedence(), PRECEDENCE_UNSPECIFIED);

    CHECK_EQ(type.channels(5).attribute(), ATTRIBUTE_STROBE);
    CHECK_EQ(type.channels(5).offset(), 6u);
    CHECK_EQ(type.channels(5).highlight_value(), 200u);
}

static void test_unknown_mode_throws() {
    bool thrown = false;
    try {
        ImportOflMode(kMiniTriPar, "lightmaXX", "does-not-exist");
    } catch (const std::runtime_error &) {
        thrown = true;
    }
    CHECK(thrown);
}

// Stretch goal 1: fine channels are not separate entries; their position becomes the fine_offset of the main channel.
static void test_pan_tilt_with_fine_channels() {
    FixtureType type = ImportOflMode(kMover, "Test", "5-channel");

    CHECK_EQ(type.channel_count(), 5u);
    CHECK_EQ(type.channels_size(), 3); // Pan, Tilt, Dimmer
    if (type.channels_size() != 3) return;

    CHECK_EQ(type.channels(0).attribute(), ATTRIBUTE_PAN);
    CHECK_EQ(type.channels(0).offset(), 1u);
    CHECK_EQ(type.channels(0).fine_offset(), 2u);

    CHECK_EQ(type.channels(1).attribute(), ATTRIBUTE_TILT);
    CHECK_EQ(type.channels(1).offset(), 3u);
    CHECK_EQ(type.channels(1).fine_offset(), 4u);

    CHECK_EQ(type.channels(2).attribute(), ATTRIBUTE_INTENSITY);
    CHECK_EQ(type.channels(2).offset(), 5u);
    CHECK_EQ(type.channels(2).fine_offset(), 0u);
}

// Stretch goal 2: an unused channel counts toward channel_count and positions, but it does not get an entry.
static void test_unused_channel_counts_but_is_not_listed() {
    FixtureType type = ImportOflMode(kUnused, "Test", "3-channel");

    CHECK_EQ(type.channel_count(), 3u);
    CHECK_EQ(type.channels_size(), 2);
    if (type.channels_size() != 2) return;

    CHECK_EQ(type.channels(0).offset(), 1u);
    CHECK_EQ(type.channels(1).offset(), 3u);
    CHECK_EQ(type.channels(1).attribute(), ATTRIBUTE_RED);
}

int main() {
    try {
        test_mini_tri_par_basics();
        test_mini_tri_par_channels();
        test_unknown_mode_throws();

        if (kRunStretchTests) {
            test_pan_tilt_with_fine_channels();
            test_unused_channel_counts_but_is_not_listed();
        }
    } catch (const std::exception &e) {
        // A test threw an unexpected exception (for example, because the importer is not implemented yet).
        std::cerr << "Unexpected exception: " << e.what() << "\n";
        return 1;
    }

    if (failures == 0) {
        std::cout << "All tests passed\n";
        return 0;
    }
    std::cerr << failures << " check(s) failed\n";
    return 1;
}
