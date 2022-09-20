/*
 * Copyright 2018 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.pass.object.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.AttributeConverter;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.eclipse.pass.object.converter.SetToStringConverter;

import com.yahoo.elide.annotation.Include;

/**
 * A Contributor is a person who contributed to a Publication. The contributor
 * model captures the person information as well as the roles they played in
 * creating the publication (e.g. author).
 *
 * @author Karen Hanson
 */

@Include
@Entity
@Table(name = "pass_contributor")
public class Contributor {
    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;

    /**
     * First name(s) of person
     */
    private String firstName;

    /**
     * Middle name(s) of person
     */
    private String middleName;

    /**
     * Last name(s) of person
     */
    private String lastName;

    /**
     * Name for display. Separate names may not be available, but a person should
     * always at least have a display name.
     */
    private String displayName;

    /**
     * Contact email for person
     */
    private String email;

    /**
     * ORCID ID for person
     */
    private String orcidId;

    /**
     * Affiliation string for person. Where Person is embedded in Submission or
     * Grant, this is the affiliation relevant to that item
     */
    @Convert(converter = SetToStringConverter.class)
    private Set<String> affiliation = new HashSet<>();

    /**
     * One or more roles that this Contributor performed for the associated
     * Publication
     */
    @Convert(converter = RoleListToStringConverter.class)
    private List<ContributorRole> roles = new ArrayList<ContributorRole>();

    /**
     * URI of the publication that this contributor is associated with
     */
    @ManyToOne
    private Publication publication;

    /**
     * URI of the user that represents the same person as this Contributor, where
     * relevant
     */
    @ManyToOne
    private User user;

    /**
     * list of possible contributor Roles
     */
    public enum ContributorRole {

        /**
         * Author role
         */
        AUTHOR("author"),

        /**
         * First author role
         */
        FIRST_AUTHOR("first-author"),

        /**
         * Last author role
         */
        LAST_AUTHOR("last-author"),

        /**
         * Corresponding author role
         */
        CORRESPONDING_AUTHOR("corresponding-author");

        private static final Map<String, ContributorRole> map = new HashMap<>(values().length, 1);

        static {
            for (ContributorRole r : values()) {
                map.put(r.value, r);
            }
        }

        private String value;

        private ContributorRole(String value) {
            this.value = value;
        }

        /**
         * Parse the role.
         *
         * @param role Serialized role string
         * @return The parsed value.
         */
        public static ContributorRole of(String role) {
            ContributorRole result = map.get(role);
            if (result == null) {
                throw new IllegalArgumentException("Invalid Role: " + role);
            }
            return result;
        }

        public String getValue() {
            return value;
        }
    }

    private static class RoleListToStringConverter implements AttributeConverter<List<ContributorRole>, String> {
        @Override
        public String convertToDatabaseColumn(List<ContributorRole> attribute) {
            return attribute == null ? null
                    : String.join(",", attribute.stream().map(ContributorRole::getValue).collect(Collectors.toList()));
        }

        @Override
        public List<ContributorRole> convertToEntityAttribute(String dbData) {
            return dbData == null ? Collections.emptyList() : Stream.of(dbData.split(",")).map(ContributorRole::of).collect(Collectors.toList());
        }
    }
}
