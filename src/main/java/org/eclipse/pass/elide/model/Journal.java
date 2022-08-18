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
package org.eclipse.pass.elide.model;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import org.eclipse.pass.elide.converter.ListToStringConverter;
import org.eclipse.pass.elide.converter.SetToStringConverter;

import com.yahoo.elide.annotation.Include;

/**
 * Describes a Journal and the path of it's participation in PubMedCentral
 *
 * @author Karen Hanson
 */

@Include
@Entity
public class Journal {
    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;

    /**
     * Name of journal
     */
    private String journalName;

    /**
     * Array of ISSN(s) for Journal
     */
    @Convert(converter = ListToStringConverter.class)
    private List<String> issns = new ArrayList<>();

    /**
     * ID of publisher
     */
    @ManyToOne
    private Publisher publisher;

    /**
     * National Library of Medicine Title Abbreviation
     */
    private String nlmta;

    /**
     * This field indicates whether a journal participates in the NIH Public Access Program by sending final
     * published article to PMC. If so, whether it requires additional processing fee.
     */
    @Enumerated(EnumType.STRING)
    private PmcParticipation pmcParticipation;
}
