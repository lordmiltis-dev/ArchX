#include "ofl_importer.h"

#include <list>
#include <map>
#include <stdexcept>
#include <string>

#include <nlohmann/json.hpp>

namespace archx
{
    namespace {
        const std::map<std::string, Attribute>& SimpleTypes() {
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

        const std::map<std::string, Attribute>& Colors() {
            static const std::map<std::string, Attribute> colors = {
                {"Red", ATTRIBUTE_RED},         {"Green", ATTRIBUTE_GREEN},     {"Blue", ATTRIBUTE_BLUE},
                {"White", ATTRIBUTE_WHITE},     {"Amber", ATTRIBUTE_AMBER},     {"UV", ATTRIBUTE_UV},
                {"Cyan", ATTRIBUTE_CYAN},       {"Magenta", ATTRIBUTE_MAGENTA}, {"Yellow", ATTRIBUTE_YELLOW},
            };
            return colors;
        }

        Attribute AttributeFor(const nlohmann::json& capability) {
            const std::string type = capability.at("type").get<std::string>();

            if (type == "ColorIntensity") {
                const auto color = Colors().find(capability.value("color", ""));
                return color != Colors().end() ? color->second : ATTRIBUTE_OTHER;
            }

            const auto found = SimpleTypes().find(type);
            return found != SimpleTypes().end() ? found->second : ATTRIBUTE_OTHER;
        }

        // Return the first usable capability for a channel, skipping "NoFunction" entries.
        const nlohmann::json* FirstCapability(const nlohmann::json& channel_info) {
            if (channel_info.contains("capability")) {
                return &channel_info.at("capability");
            }
            if (channel_info.contains("capabilities")) {
                for (const auto& capability : channel_info.at("capabilities")) {
                    if (capability.at("type").get<std::string>() != "NoFunction") {
                        return &capability;
                    }
                }
            }
            return nullptr;
        }

    }  // namespace

    FixtureType ImportOflMode(const std::string& json_text,
                              const std::string& manufacturer_name,
                              const std::string& mode_name)
    {
        // Steps (details in the work plan):
        //
        //  1. Parse the JSON:                  auto fixture = nlohmann::json::parse(json_text);
        //  2. Find the mode with the matching name in fixture.at("modes"), otherwise throw std::runtime_error.
        //  3. Determine the position of each channel name (first pass): iterate through mode.at("channels");
        //     the position starts at 1. A null entry means an unused channel: it counts toward the total but not a ChannelDef.
        //     An object entry (matrix channels) is not supported yet: throw std::runtime_error.
        //  4. Detect fine channels: if a channel in fixture.at("availableChannels") has the field "fineChannelAliases",
        //     the first alias is the fine channel. It does not get its own ChannelDef; its position becomes the fine_offset of the main channel.
        //  5. Build ChannelDefs (second pass): offset, attribute (via AttributeFor), name,
        //     highlight_value (field "highlightValue" if it is numeric),
        //     precedence (field "precedence": "HTP" or "LTP"), fine_offset.
        //     The capability may be stored in "capability" (an object) or in "capabilities" (a list).
        //     For a list, the first entry that is not "NoFunction" counts.
        //  6. Fill FixtureType: name, manufacturer, mode_name, library_source ("ofl"),
        //     library_key ("<manufacturerKey>/<fixtureKey>/<mode name>"), library_revision (meta.lastModifyDate),
        //     channel_count (number of entries in mode.at("channels"), including unused ones).

        const nlohmann::json* mode = nullptr;
        nlohmann::json fixture;

        try
        {
            fixture = nlohmann::json::parse(json_text);
        }
        catch (const nlohmann::json::exception& e)
        {
            throw std::runtime_error(std::string("Invalid Json file: ") + e.what());
        }

        for (const auto& candidate : fixture.at("modes"))
        {
            if (candidate.at("name").get<std::string>() == mode_name)
            {
                mode = &candidate;
                break;
            }
            if (mode == nullptr)
            {
                throw std::runtime_error(std::string("Invalid mode: ") + mode_name);
            }
        }

        const nlohmann::json& available = fixture.at("availableChannels");
        FixtureType type;
        unsigned pos = 0;

        for (const auto& entry : mode->at("channels"))
        {
            pos++;
            if (entry.is_number()) continue;
            if (entry.is_object()) throw std::runtime_error("Matrix-Channels are not supported yet");

            const std::string channel_name = entry.get<std::string>();

            const nlohmann::json& info = available.at(channel_name);  // description of this channel

            ChannelDef* channel = type.add_channels();
            channel->set_offset(pos);
            channel->set_name(channel_name);

            const nlohmann::json* capability = FirstCapability(info);
            channel->set_attribute(capability != nullptr ? AttributeFor(*capability) : ATTRIBUTE_OTHER);

            if (info.contains("highlightValue") && info.at("highlightValue").is_number())
            {
                channel->set_highlight_value(info.at("highlightValue").get<unsigned>());
            }
            if (info.contains("precedence"))
            {
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
