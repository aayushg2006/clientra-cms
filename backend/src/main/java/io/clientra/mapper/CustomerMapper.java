package io.clientra.mapper;

import io.clientra.dto.CustomerDtos.AddressDto;
import io.clientra.dto.CustomerDtos.CustomerDto;
import io.clientra.dto.CustomerDtos.TagDto;
import io.clientra.dto.UserDto;
import io.clientra.entity.Address;
import io.clientra.entity.Customer;
import io.clientra.entity.Tag;
import io.clientra.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Mapper
public interface CustomerMapper {

    @Mapping(target = "fullName", expression = "java(c.getFullName())")
    @Mapping(target = "tags", expression = "java(tags(c.getTags()))")
    @Mapping(target = "lastContactAt", source = "lastContactAt")
    CustomerDto toDto(Customer c, Instant lastContactAt);

    default CustomerDto toDto(Customer c) {
        return toDto(c, null);
    }

    TagDto toDto(Tag tag);

    AddressDto toDto(Address a);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "customer", ignore = true)
    Address toEntity(AddressDto dto);

    default UserDto.Ref owner(User u) {
        return UserDto.Ref.of(u);
    }

    default List<TagDto> tags(Set<Tag> tags) {
        return tags.stream().sorted(Comparator.comparing(Tag::getName)).map(this::toDto).toList();
    }
}
