(function($) {

  var userP = null;
  var appendGroupDetails = function(tr) { return function(resp) {
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
      userP || (userP = $SERVICE.whoami());
      var li = $('<li>');
      userP.done(function(user) {
        if (member.name == user.username) {
          li.append("<em>you</em>")
        } else {
          li.text(member.name);
        }
        if (member.name == group.owner) {
          li.append(" (owner)");
        }
      });
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

  var getGroups = function() {
    $SERVICE.get("groups").then(function(resp) {
      var $table = $('#groups tbody').empty();
      if (resp.groups.length) {
        $('#no-groups').hide();
        resp.groups.forEach(function(group) {
          var tr = $('<tr>');
          tr.append('<td><input type="checkbox" data-name="' + group.name + '" data-owner="' + group.owner + '" data-uuid="' + group.uuid + '"/></td>')
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
  var addGroupForm = $('#add-group-form');
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
  var confirmDeletionForm = $('#delete-groups-form');
  $('#delete-groups').click(function(evt) {
    var dialogue = new Boxy(confirmDeletionForm, {
      title: "Delete/Leave Groups",
      modal: true,
      show: false
    });
    userP || (userP = $SERVICE.whoami());
    var toDelete = confirmDeletionForm.find('.to-delete').empty();
    var toLeave = confirmDeletionForm.find('.to-leave').empty();
    userP.then(function(user) {
      $('#groups input[type="checkbox"]').each(function() {
        var $el = $(this);
        if ($el.is(':checked')) {
          var owner = $el.data('owner');
          var name = $el.data('name');

          var ul = (owner == user.username) ? toDelete : toLeave;
          ul.append($('<li>').text(name));
        }
      });
    });

    dialogue.show();
  });
}).call(this, jQuery);
