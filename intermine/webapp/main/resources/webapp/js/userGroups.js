(function($) {

  var userP, presentMember, appendGroupDetails, deleteHandler, leaveHandler, getGroups, addGroupForm = $('#add-group-form');

  userP = $SERVICE.whoami();

  presentMember = function(member, group, li) { return function(user) {
    if (member.name == user.username) {
      li.append("<em>you</em>")
    } else {
      li.text(member.name);
    }
    if (member.name == group.owner) {
      li.append(" (owner)");
    }
  }};

  appendGroupDetails = function(tr) { return function(resp) {
    var group = resp.group;
    var detailsCell = tr.find('.details');
    console.log(group);
    detailsCell
      .append(group.members.length + " members, ")
      .append(group.lists.length + " lists");
    var moreDetails = $('<div class="group-details"><div class="members"><h4>Members</h4><ul></ul><button class="add">Add member</button></div><div class="lists"><h4>Lists</h4><ul></ul><button class="add">Add list</button></div></div>');
    var members = moreDetails.find('.members ul');
    var lists =  moreDetails.find('.lists ul');
    group.members.forEach(function(member) {
      var li = $('<li>');
      userP.done(presentMember(member, group, li));
      members.append(li);
    });
    group.lists.forEach(function(list) {
      lists.append(
        $('<li>').text(list.name + " (" + list.size + " " + list.type + ")"));
    });
    var detailsRow = $('<tr><td colspan=4></td></tr>');
    detailsRow.find('td').append(moreDetails);
    tr.after(detailsRow);
    detailsRow.hide();
    detailsCell.click(function() {
      detailsRow.slideToggle();
    });
  }};

  function ungrouper(action, consequence, pathInfo) { return function(group) { return function(evt) {
    evt.preventDefault();
    evt.stopPropagation();
    Boxy.confirm("Do you really want to " + action + " the " + group.name + " group? " + consequence, function yesDoIt() {
      $SERVICE.makeRequest("DELETE", "groups/" + group.uuid + (pathInfo || '')).then(getGroups);
    });
  }}}

  deleteHandler = ungrouper("delete", 'All members and lists will be permanently removed.');
  leaveHandler = ungrouper("leave", 'You will lose access to all lists shared with this group', '/members');

  getGroups = function() {
    $SERVICE.get("groups").then(function(resp) {
      var $table = $('#groups tbody').empty();
      if (resp.groups.length) {
        $('#no-groups').hide();
        resp.groups.forEach(function(group) {
          var tr = $('<tr>');
          var remover = $('<button>');
          userP.then(function(user) {
            var isOwner = group.owner === user.username;
            remover.text(isOwner ? "Delete" : "Leave");
            remover.click(isOwner ? deleteHandler(group) : leaveHandler(group));
          });
          tr.append($('<td>').append(remover))
            .append('<td>' + group.name + '</td>')
            .append('<td>' + group.description + '</td>');
          var detailsCell = $('<td class="details">');
          tr.append(detailsCell);
          $SERVICE.get("groups/" + group.uuid).then(appendGroupDetails(tr));
          $table.append(tr);
        });
      } else {
        $('#no-groups').show();
        $table.hide();
      }
    });
  };
  $(getGroups);

  addGroupForm.submit(function(evt) {
    evt.preventDefault();
    evt.stopPropagation();
  });
  $('#add-group').click(function(evt) {
    var dialogue = new Boxy(addGroupForm, {
      title: "New Group Details",
      modal: true,
      show: false
    });
    addGroupForm.find('button.confirm').unbind('click').click(function(evt) {
      var name = addGroupForm.find('.group-name').val();
      var description = addGroupForm.find('.group-description').val();
      $SERVICE.post("groups", {name: name, description: description})
              .then(getGroups);
      dialogue.hide();
    });
    dialogue.show();
  });

}).call(this, jQuery);
