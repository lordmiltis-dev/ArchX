// Implements conversion from Open Fixture Library JSON into the showfile FixtureType format.

#include "ofl_importer.h"

#include <map>
#include <stdexcept>
#include <string>

#include <nlohmann/json.hpp>

namespace archx {
    namespace {
        const std::map<std::string, Attribute> &SimpleTypes() {
            static const std::map<std::string, Attribute> types = {
                {"Intensity", ATTRIBUTE_INTENSITY},
                {"ColorTemperature", ATTRIBUTE_COLOR_TEMPERATURE},
                {"Pan", ATTRIBUTE_PAN},
                {"PanContinuous", ATTRIBUTE_PAN},
                {"Tilt", ATTRIBUTE_TILT},
                {"TiltContinuous", ATTRIBUTE_TILT},
                {"PanTiltSpeed", ATTRIBUTE_PAN_TILT_SPEED},
                {"ShutterStrobe", ATTRIBUTE_STROBE},
                {"StrobeSpeed", ATTRIBUTE_STROBE},
                {"StrobeDuration", ATTRIBUTE_STROBE},
                {"Zoom", ATTRIBUTE_ZOOM},
                {"Focus", ATTRIBUTE_FOCUS},
                {"Iris", ATTRIBUTE_IRIS},
                {"Frost", ATTRIBUTE_FROST},
                {"Prism", ATTRIBUTE_PRISM},
                {"PrismRotation", ATTRIBUTE_PRISM_ROTATION},
                {"WheelSlotRotation", ATTRIBUTE_GOBO_ROTATION},
                {"WheelRotation", ATTRIBUTE_GOBO_ROTATION},
                {"BladeInsertion", ATTRIBUTE_FRAMING_BLADE},
                {"BladeRotation", ATTRIBUTE_FRAMING_ROTATION},
                {"BladeSystemRotation", ATTRIBUTE_FRAMING_ROTATION},
                {"Effect", ATTRIBUTE_EFFECT},
                {"ColorPreset", ATTRIBUTE_EFFECT},
                {"EffectSpeed", ATTRIBUTE_SPEED},
                {"EffectDuration", ATTRIBUTE_SPEED},
                {"Speed", ATTRIBUTE_SPEED},
                {"SoundSensitivity", ATTRIBUTE_SOUND_SENSITIVITY},
                {"Maintenance", ATTRIBUTE_CONTROL},
            };
            return types;
        }

        const std::map<std::string, Attribute> &Colors() {
            static const std::map<std::string, Attribute> colors = {
                {"Red", ATTRIBUTE_RED}, {"Green", ATTRIBUTE_GREEN}, {"Blue", ATTRIBUTE_BLUE},
                {"White", ATTRIBUTE_WHITE}, {"Amber", ATTRIBUTE_AMBER}, {"UV", ATTRIBUTE_UV},
                {"Cyan", ATTRIBUTE_CYAN}, {"Magenta", ATTRIBUTE_MAGENTA}, {"Yellow", ATTRIBUTE_YELLOW},
            };
            return colors;
        }

        Attribute AttributeFor(const nlohmann::json &capability) {
            const std::string type = capability.at("type").get<std::string>();

            if (type == "ColorIntensity") {
                const auto color = Colors().find(capability.value("color", ""));
                return color != Colors().end() ? color->second : ATTRIBUTE_OTHER;
            }

            const auto found = SimpleTypes().find(type);
            return found != SimpleTypes().end() ? found->second : ATTRIBUTE_OTHER;
        }

        // Return the first usable capability for a channel, skipping "NoFunction" entries.
        const nlohmann::json *FirstCapability(const nlohmann::json &channel_info) {
            if (channel_info.contains("capability")) {
                return &channel_info.at("capability");
            }
            if (channel_info.contains("capabilities")) {
                for (const auto &capability: channel_info.at("capabilities")) {
                    if (capability.at("type").get<std::string>() != "NoFunction") {
                        return &capability;
                    }
                }
            }
            return nullptr;
        }
    } // namespace

    FixtureType ImportOflMode(const std::string &json_text,
                              const std::string &manufacturer_name,
                              const std::string &mode_name) {
        const nlohmann::json *mode = nullptr;
        nlohmann::json fixture;

        try {
            fixture = nlohmann::json::parse(json_text);
        } catch (const nlohmann::json::exception &e) {
            throw std::runtime_error(std::string("Invalid Json file: ") + e.what());
        }

        for (const auto &candidate: fixture.at("modes")) {
            if (candidate.at("name").get<std::string>() == mode_name) {
                mode = &candidate;
                break;
            }
        }
        if (mode == nullptr) {
            throw std::runtime_error(std::string("Invalid mode: ") + mode_name);
        }


        const nlohmann::json &available = fixture.at("availableChannels");
        FixtureType type;
        unsigned pos = 0;

        for (const auto &entry: mode->at("channels")) {
            pos++;
            if (entry.is_null()) continue;
            if (entry.is_object()) throw std::runtime_error("Matrix-Channels are not supported yet");

            const std::string channel_name = entry.get<std::string>();

            const nlohmann::json &info = available.at(channel_name); // description of this channel

            ChannelDef *channel = type.add_channels();
            channel->set_offset(pos);
            channel->set_name(channel_name);

            const nlohmann::json *capability = FirstCapability(info);
            channel->set_attribute(capability != nullptr ? AttributeFor(*capability) : ATTRIBUTE_OTHER);

            if (info.contains("highlightValue") && info.at("highlightValue").is_number()) {
                channel->set_highlight_value(info.at("highlightValue").get<unsigned>());
            }
            if (info.contains("precedence")) {
                const std::string precedence = info.at("precedence").get<std::string>();
                if (precedence == "HTP") channel->set_precedence(PRECEDENCE_HTP);
                if (precedence == "LTP") channel->set_precedence(PRECEDENCE_LTP);
            }
        }

        type.set_name(fixture.at("name").get<std::string>());
        type.set_manufacturer(manufacturer_name);
        type.set_mode_name(mode_name);
        type.set_library_source("ofl");
        type.set_library_key(fixture.at("manufacturerKey").get<std::string>() + "/" +
                             fixture.at("fixtureKey").get<std::string>() + "/" + mode_name);
        type.set_library_revision(fixture.at("meta").at("lastModifyDate").get<std::string>());
        type.set_channel_count(pos); // count all entries, including unused ones
        return type;
    }
}

// namespace archx
