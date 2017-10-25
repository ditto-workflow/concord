package com.walmartlabs.concord.server.api.team;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.UUID;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public class TeamEntry implements Serializable {

    private final UUID id;

    @NotNull
    @ConcordKey
    private final String name;

    @Size(max = 2048)
    private final String description;

    private final Boolean enabled;

    private final TeamVisibility visibility;

    @JsonCreator
    public TeamEntry(@JsonProperty("id") UUID id,
                     @JsonProperty("name") String name,
                     @JsonProperty("description") String description,
                     @JsonProperty("boolean") Boolean enabled,
                     @JsonProperty("visiblity") TeamVisibility visibility) {

        this.id = id;
        this.name = name;
        this.description = description;
        this.enabled = enabled;
        this.visibility = visibility;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public TeamVisibility getVisibility() {
        return visibility;
    }

    @Override
    public String toString() {
        return "TeamEntry{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", enabled=" + enabled +
                ", visibility=" + visibility +
                '}';
    }
}
