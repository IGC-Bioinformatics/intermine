package org.intermine.web;

import java.util.Map.Entry;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.intermine.api.bag.Group;
import org.intermine.api.bag.SharedBagManager;
import org.intermine.api.profile.InterMineBag;
import org.intermine.api.profile.Profile;
import org.intermine.api.profile.ProfileManager;

public class GroupBinding {
    
    private SharedBagManager sbm;
    private ProfileManager profileManager;

    public GroupBinding(ProfileManager profileManager) {
        SharedBagManager sbm = SharedBagManager.getInstance(profileManager);
        this.profileManager = profileManager;
        this.sbm = sbm;
    }
    
    public void marshalGroups(Profile owner, XMLStreamWriter writer)
            throws XMLStreamException {
        writer.writeStartElement("groups");
        writer.writeCharacters("\n");
        for (Group group: sbm.getOwnGroups(owner)) {
            writer.writeCharacters("  ");
            marshal(group, writer);
        }
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }
    
    public void marshal(Group group, XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement("group");
        writer.writeCharacters("\n  ");

        // Write Properties
        for (Entry<String, Object> pair: group.toMap().entrySet()) {
            writer.writeCharacters("  ");
            writer.writeStartElement(pair.getKey());
            writer.writeCharacters(String.valueOf(pair.getValue()));
            writer.writeEndElement();
            writer.writeCharacters("\n  ");
        }
        
        // Members
        for (Profile member: sbm.getGroupMembers(group)) {
            writer.writeCharacters("  ");
            writer.writeStartElement("member");
            writer.writeCharacters(member.getName());
            writer.writeEndElement();
            writer.writeCharacters("\n  ");
        }

        // Bags
        for (InterMineBag bag: sbm.getBagsInGroup(group)) {
            writer.writeCharacters("  ");
            writer.writeStartElement("bag");
            writer.writeAttribute("owner",
                    profileManager.getProfile(bag.getProfileId()).getName());
            writer.writeCharacters(bag.getName());
            writer.writeEndElement();
            writer.writeCharacters("\n");
        }

        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

}
