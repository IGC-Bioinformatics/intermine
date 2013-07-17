(function($) {

  var userP, presentMember, appendGroupDetails, deleteHandler, leaveHandler, getGroups, addGroupForm = $('#add-group-form');

  userP = $SERVICE.whoami();

  function elemer(tagName) { return function(content) { return $('<' + tagName + '>').append(content); } }
  var td = elemer('td'), strong = elemer('strong'), em = elemer('em'), button = elemer('button'), a = elemer('a'), li = elemer('li'), span = elemer('span');

  presentMember = function(member, group, li) { return function(user) {
    if (member.username == user.username) {
      li.append("<em>you</em>")
    } else {
      li.text(member.username);
    }
    if (member.username == group.owner.username) {
      li.append(" (owner)");
    }
  }};

  function ignore(evt) {
      evt.preventDefault();
      evt.stopPropagation();
  }

  function groupAddHandler(dialogue, input, group, pathInfo) { return function (evt) {
      ignore(evt);
      var name = input.val();
      $SERVICE.post("groups/" + group.uuid + pathInfo, {name: name})
        .then(getGroups)
        .fail(FailureNotification.notify);
      dialogue.hide();
  }}

  function dialogueCloser(dialogue) { return function (evt) {
      evt.stopPropagation();
      dialogue.hide();
  }}

  function selectAndAddListTo(group) { return function(evt) {
    ignore(evt);
    var form = $('<form><div><label>Choose a list:</label><select></select></div><button class="add">Add</button><button class="cancel">Cancel</button></form>');
    var selector = form.find('select');
    var option = elemer('option');
    var dialogue = new Boxy(form, {modal: true, show: false});
    form.submit(ignore);
    $SERVICE.fetchLists().done(function(lists) {
      lists.forEach(function(list) {
        if (list.authorized) selector.append(option(list.name));
      });
      dialogue.center('x');
    });
    form.find('button.add').click(groupAddHandler(dialogue, selector, group, '/lists'));
    form.find('button.cancel').click(dialogueCloser(dialogue));
    dialogue.show();
  }}

  function openMemberSelectionDialogue(group) { return function(evt) {
    ignore(evt);
    var form = $('<form><div><label>Enter a member name:</label><input type="text" name="memberName"></div><button class="add">Add</button><button class="cancel">Cancel</button></form>');
    var input = form.find('input');
    var dialogue = new Boxy(form, {modal: true, show: false});
    form.submit(ignore);
    form.find('button.add').click(groupAddHandler(dialogue, input, group, '/members'));
    form.find('button.cancel').click(dialogueCloser(dialogue));
    dialogue.show();
  }}

  function bagLink(list) {
    return $SERVICE.root.replace(/service\/$/,'') + "bagDetails.do?name=" + escape(list.name);
  }
  function bagLabel(list) {
    return list.name + " (" + list.size + " " + list.type + "s)";
  }


  function addListItemHandler(lists, group) { return function(list) {
    var link = a(span().text(bagLabel(list))).attr({href: bagLink(list)});
    var item = li(link);
    if (list.authorized) {
      var deleter = button('Unshare');
      item.prepend(deleter);
      deleter.click(function(event) {
        ignore(event);
        $SERVICE.makeRequest("DELETE", "groups/" + group.uuid + "/lists", {name: list.name})
                .then(getGroups, FailureNotification.notify);
      });
    }
    lists.append(item);
  }}

  function addMemberItemHandler(members, group) { return function(member) {
    var li = $('<li>');
    userP.done(presentMember(member, group, li));
    members.append(li);
    userP.then(function(user) {
      if (user.username == group.owner.username && user.username != member.username) {
        var evicter = button("Remove").click(function(evt) {
          ignore(evt);
          $SERVICE.makeRequest("DELETE", "groups/" + group.uuid + "/members", {name: member.username})
                  .then(getGroups, FailureNotification.notify);
        });
        li.prepend(evicter);
      }
    });
  }}

  appendGroupDetails = function(tr) { return function(resp) {
    var group = resp.group;
    var detailsCell = tr.find('.details');
    var a = elemer('a');
    detailsCell.append(a(group.members.length + " members, " + group.lists.length + " lists").attr({href: '#'}));
    var moreDetails = $('<div class="group-details"><div class="members"><h4>Members</h4><ul></ul><button class="add">Add member</button></div><div class="lists"><h4>Lists</h4><ul></ul><button class="add">Add list</button></div></div>');
    var members = moreDetails.find('.members ul');
    var lists =  moreDetails.find('.lists ul');
    group.members.forEach(addMemberItemHandler(members, group));
    userP.then(function(user) {
      var memberAdder = moreDetails.find('.members button.add');
      if (user.username != group.owner.username) {
        memberAdder.remove();
      } else {
        memberAdder.click(openMemberSelectionDialogue(group));
      }
    });
    group.lists.forEach(addListItemHandler(lists, group));
    moreDetails.find('.lists button.add').click(selectAndAddListTo(group));
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
    Boxy.confirm("Do you really want to " + action + " '" + group.name + "'? " + consequence, function yesDoIt() {
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
            var isOwner = group.owner.username === user.username;
            remover.text(isOwner ? "Delete" : "Leave");
            remover.click(isOwner ? deleteHandler(group) : leaveHandler(group));
          });
          tr.append(td(remover))
            .append(td(strong(group.name)))
            .append(td(em(group.description)));
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
