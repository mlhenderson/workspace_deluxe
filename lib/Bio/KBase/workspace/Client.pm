package Bio::KBase::workspace::Client;

use JSON::RPC::Client;
use strict;
use Data::Dumper;
use URI;
use Bio::KBase::Exceptions;
use Bio::KBase::AuthToken;

# Client version should match Impl version
# This is a Semantic Version number,
# http://semver.org
our $VERSION = "0.1.0";

=head1 NAME

Bio::KBase::workspace::Client

=head1 DESCRIPTION


The workspace service at its core is a storage and retrieval system for 
typed objects. Objects are organized by the user into one or more workspaces.

Features:

Versioning of objects
Data provenenance
Object to object references
Workspace sharing
**Add stuff here***


BINARY DATA:
All binary data must be hex encoded prior to storage in a workspace. 
Attempting to send binary data via a workspace client will cause errors.


=cut

sub new
{
    my($class, $url, @args) = @_;
    
    if (!defined($url))
    {
	$url = 'http://kbase.us/services/workspace/';
    }

    my $self = {
	client => Bio::KBase::workspace::Client::RpcClient->new,
	url => $url,
    };

    # This module requires authentication.
    #
    # We create an auth token, passing through the arguments that we were (hopefully) given.

    {
	my $token = Bio::KBase::AuthToken->new(@args);
	
	if ($token->error_message)
	{
	    die "Authentication failed: " . $token->error_message;
	}
	$self->{token} = $token->token;
	$self->{client}->{token} = $token->token;
    }

    my $ua = $self->{client}->ua;	 
    my $timeout = $ENV{CDMI_TIMEOUT} || (30 * 60);	 
    $ua->timeout($timeout);
    bless $self, $class;
    #    $self->_validate_version();
    return $self;
}




=head2 create_workspace

  $metadata = $obj->create_workspace($params)

=over 4

=item Parameter and return types

=begin html

<pre>
$params is a CreateWorkspaceParams
$metadata is a workspace_metadata
CreateWorkspaceParams is a reference to a hash where the following keys are defined:
	workspace has a value which is a ws_name
	globalread has a value which is a permission
	description has a value which is a string
ws_name is a string
permission is a string
workspace_metadata is a reference to a list containing 6 items:
	0: (id) a ws_id
	1: (workspace) a ws_name
	2: (owner) a username
	3: (moddate) a timestamp
	4: (user_permission) a permission
	5: (globalread) a permission
ws_id is an int
username is a string
timestamp is a string

</pre>

=end html

=begin text

$params is a CreateWorkspaceParams
$metadata is a workspace_metadata
CreateWorkspaceParams is a reference to a hash where the following keys are defined:
	workspace has a value which is a ws_name
	globalread has a value which is a permission
	description has a value which is a string
ws_name is a string
permission is a string
workspace_metadata is a reference to a list containing 6 items:
	0: (id) a ws_id
	1: (workspace) a ws_name
	2: (owner) a username
	3: (moddate) a timestamp
	4: (user_permission) a permission
	5: (globalread) a permission
ws_id is an int
username is a string
timestamp is a string


=end text

=item Description

Creates a new workspace.

=back

=cut

sub create_workspace
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 1)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function create_workspace (received $n, expecting 1)");
    }
    {
	my($params) = @args;

	my @_bad_arguments;
        (ref($params) eq 'HASH') or push(@_bad_arguments, "Invalid type for argument 1 \"params\" (value was \"$params\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to create_workspace:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'create_workspace');
	}
    }

    my $result = $self->{client}->call($self->{url}, {
	method => "Workspace.create_workspace",
	params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'create_workspace',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return wantarray ? @{$result->result} : $result->result->[0];
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method create_workspace",
					    status_line => $self->{client}->status_line,
					    method_name => 'create_workspace',
				       );
    }
}



=head2 get_workspace_metadata

  $meta = $obj->get_workspace_metadata($wsi)

=over 4

=item Parameter and return types

=begin html

<pre>
$wsi is a WorkspaceIdentity
$meta is a workspace_metadata
WorkspaceIdentity is a reference to a hash where the following keys are defined:
	workspace has a value which is a ws_name
	id has a value which is a ws_id
ws_name is a string
ws_id is an int
workspace_metadata is a reference to a list containing 6 items:
	0: (id) a ws_id
	1: (workspace) a ws_name
	2: (owner) a username
	3: (moddate) a timestamp
	4: (user_permission) a permission
	5: (globalread) a permission
username is a string
timestamp is a string
permission is a string

</pre>

=end html

=begin text

$wsi is a WorkspaceIdentity
$meta is a workspace_metadata
WorkspaceIdentity is a reference to a hash where the following keys are defined:
	workspace has a value which is a ws_name
	id has a value which is a ws_id
ws_name is a string
ws_id is an int
workspace_metadata is a reference to a list containing 6 items:
	0: (id) a ws_id
	1: (workspace) a ws_name
	2: (owner) a username
	3: (moddate) a timestamp
	4: (user_permission) a permission
	5: (globalread) a permission
username is a string
timestamp is a string
permission is a string


=end text

=item Description

Get a workspace's metadata.

=back

=cut

sub get_workspace_metadata
{
    my($self, @args) = @_;

# Authentication: optional

    if ((my $n = @args) != 1)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function get_workspace_metadata (received $n, expecting 1)");
    }
    {
	my($wsi) = @args;

	my @_bad_arguments;
        (ref($wsi) eq 'HASH') or push(@_bad_arguments, "Invalid type for argument 1 \"wsi\" (value was \"$wsi\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to get_workspace_metadata:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'get_workspace_metadata');
	}
    }

    my $result = $self->{client}->call($self->{url}, {
	method => "Workspace.get_workspace_metadata",
	params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'get_workspace_metadata',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return wantarray ? @{$result->result} : $result->result->[0];
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method get_workspace_metadata",
					    status_line => $self->{client}->status_line,
					    method_name => 'get_workspace_metadata',
				       );
    }
}



=head2 get_workspace_description

  $description = $obj->get_workspace_description($wsi)

=over 4

=item Parameter and return types

=begin html

<pre>
$wsi is a WorkspaceIdentity
$description is a string
WorkspaceIdentity is a reference to a hash where the following keys are defined:
	workspace has a value which is a ws_name
	id has a value which is a ws_id
ws_name is a string
ws_id is an int

</pre>

=end html

=begin text

$wsi is a WorkspaceIdentity
$description is a string
WorkspaceIdentity is a reference to a hash where the following keys are defined:
	workspace has a value which is a ws_name
	id has a value which is a ws_id
ws_name is a string
ws_id is an int


=end text

=item Description

Get a workspace's description.

=back

=cut

sub get_workspace_description
{
    my($self, @args) = @_;

# Authentication: optional

    if ((my $n = @args) != 1)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function get_workspace_description (received $n, expecting 1)");
    }
    {
	my($wsi) = @args;

	my @_bad_arguments;
        (ref($wsi) eq 'HASH') or push(@_bad_arguments, "Invalid type for argument 1 \"wsi\" (value was \"$wsi\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to get_workspace_description:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'get_workspace_description');
	}
    }

    my $result = $self->{client}->call($self->{url}, {
	method => "Workspace.get_workspace_description",
	params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'get_workspace_description',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return wantarray ? @{$result->result} : $result->result->[0];
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method get_workspace_description",
					    status_line => $self->{client}->status_line,
					    method_name => 'get_workspace_description',
				       );
    }
}



=head2 set_permissions

  $obj->set_permissions($params)

=over 4

=item Parameter and return types

=begin html

<pre>
$params is a SetPermissionsParams
SetPermissionsParams is a reference to a hash where the following keys are defined:
	workspace has a value which is a ws_name
	id has a value which is a ws_id
	new_permission has a value which is a permission
	users has a value which is a reference to a list where each element is a username
ws_name is a string
ws_id is an int
permission is a string
username is a string

</pre>

=end html

=begin text

$params is a SetPermissionsParams
SetPermissionsParams is a reference to a hash where the following keys are defined:
	workspace has a value which is a ws_name
	id has a value which is a ws_id
	new_permission has a value which is a permission
	users has a value which is a reference to a list where each element is a username
ws_name is a string
ws_id is an int
permission is a string
username is a string


=end text

=item Description

Set permissions for a workspace.

=back

=cut

sub set_permissions
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 1)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function set_permissions (received $n, expecting 1)");
    }
    {
	my($params) = @args;

	my @_bad_arguments;
        (ref($params) eq 'HASH') or push(@_bad_arguments, "Invalid type for argument 1 \"params\" (value was \"$params\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to set_permissions:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'set_permissions');
	}
    }

    my $result = $self->{client}->call($self->{url}, {
	method => "Workspace.set_permissions",
	params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'set_permissions',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return;
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method set_permissions",
					    status_line => $self->{client}->status_line,
					    method_name => 'set_permissions',
				       );
    }
}



=head2 get_permissions

  $perms = $obj->get_permissions($wsi)

=over 4

=item Parameter and return types

=begin html

<pre>
$wsi is a WorkspaceIdentity
$perms is a reference to a hash where the key is a username and the value is a permission
WorkspaceIdentity is a reference to a hash where the following keys are defined:
	workspace has a value which is a ws_name
	id has a value which is a ws_id
ws_name is a string
ws_id is an int
username is a string
permission is a string

</pre>

=end html

=begin text

$wsi is a WorkspaceIdentity
$perms is a reference to a hash where the key is a username and the value is a permission
WorkspaceIdentity is a reference to a hash where the following keys are defined:
	workspace has a value which is a ws_name
	id has a value which is a ws_id
ws_name is a string
ws_id is an int
username is a string
permission is a string


=end text

=item Description

Get permissions for a workspace.

=back

=cut

sub get_permissions
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 1)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function get_permissions (received $n, expecting 1)");
    }
    {
	my($wsi) = @args;

	my @_bad_arguments;
        (ref($wsi) eq 'HASH') or push(@_bad_arguments, "Invalid type for argument 1 \"wsi\" (value was \"$wsi\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to get_permissions:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'get_permissions');
	}
    }

    my $result = $self->{client}->call($self->{url}, {
	method => "Workspace.get_permissions",
	params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'get_permissions',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return wantarray ? @{$result->result} : $result->result->[0];
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method get_permissions",
					    status_line => $self->{client}->status_line,
					    method_name => 'get_permissions',
				       );
    }
}



sub version {
    my ($self) = @_;
    my $result = $self->{client}->call($self->{url}, {
        method => "Workspace.version",
        params => [],
    });
    if ($result) {
        if ($result->is_error) {
            Bio::KBase::Exceptions::JSONRPC->throw(
                error => $result->error_message,
                code => $result->content->{code},
                method_name => 'get_permissions',
            );
        } else {
            return wantarray ? @{$result->result} : $result->result->[0];
        }
    } else {
        Bio::KBase::Exceptions::HTTP->throw(
            error => "Error invoking method get_permissions",
            status_line => $self->{client}->status_line,
            method_name => 'get_permissions',
        );
    }
}

sub _validate_version {
    my ($self) = @_;
    my $svr_version = $self->version();
    my $client_version = $VERSION;
    my ($cMajor, $cMinor) = split(/\./, $client_version);
    my ($sMajor, $sMinor) = split(/\./, $svr_version);
    if ($sMajor != $cMajor) {
        Bio::KBase::Exceptions::ClientServerIncompatible->throw(
            error => "Major version numbers differ.",
            server_version => $svr_version,
            client_version => $client_version
        );
    }
    if ($sMinor < $cMinor) {
        Bio::KBase::Exceptions::ClientServerIncompatible->throw(
            error => "Client minor version greater than Server minor version.",
            server_version => $svr_version,
            client_version => $client_version
        );
    }
    if ($sMinor > $cMinor) {
        warn "New client version available for Bio::KBase::workspace::Client\n";
    }
    if ($sMajor == 0) {
        warn "Bio::KBase::workspace::Client version is $svr_version. API subject to change.\n";
    }
}

=head1 TYPES



=head2 boolean

=over 4



=item Description

A boolean. 0 = false, other = true.


=item Definition

=begin html

<pre>
an int
</pre>

=end html

=begin text

an int

=end text

=back



=head2 ws_id

=over 4



=item Description

The unique, permanent numerical ID of a workspace.


=item Definition

=begin html

<pre>
an int
</pre>

=end html

=begin text

an int

=end text

=back



=head2 ws_name

=over 4



=item Description

A string used as a name for a workspace.
Any string consisting of alphanumeric characters and "_" is acceptable.


=item Definition

=begin html

<pre>
a string
</pre>

=end html

=begin text

a string

=end text

=back



=head2 permission

=over 4



=item Description

Represents the permissions a user or users have to a workspace:

        'a' - administrator. All operations allowed.
        'w' - read/write.
        'r' - read.
        'n' - no permissions.


=item Definition

=begin html

<pre>
a string
</pre>

=end html

=begin text

a string

=end text

=back



=head2 username

=over 4



=item Description

Login name of a KBase user account.


=item Definition

=begin html

<pre>
a string
</pre>

=end html

=begin text

a string

=end text

=back



=head2 timestamp

=over 4



=item Description

A time, e.g. 2012-12-17T23:24:06.


=item Definition

=begin html

<pre>
a string
</pre>

=end html

=begin text

a string

=end text

=back



=head2 WorkspaceIdentity

=over 4



=item Description

A workspace identifier.

                Select a workspace by one, and only one, of the numerical id or name, where the
                name can also be a KBase ID including the numerical id, e.g. kb|ws.35.
                ws_id - the numerical ID of the workspace.
                ws_name workspace - name of the workspace or the workspace ID in KBase format, e.g. kb|ws.78.


=item Definition

=begin html

<pre>
a reference to a hash where the following keys are defined:
workspace has a value which is a ws_name
id has a value which is a ws_id

</pre>

=end html

=begin text

a reference to a hash where the following keys are defined:
workspace has a value which is a ws_name
id has a value which is a ws_id


=end text

=back



=head2 workspace_metadata

=over 4



=item Description

Meta data associated with a workspace.

        ws_id id - the numerical ID of the workspace.
        ws_name workspace - name of the workspace.
        username owner - name of the user who owns (e.g. created) this workspace.
        timestamp moddate - date when the workspace was last modified.
        permission user_permission - permissions for the authenticated user of this workspace.
        permission globalread - whether this workspace is globally readable.


=item Definition

=begin html

<pre>
a reference to a list containing 6 items:
0: (id) a ws_id
1: (workspace) a ws_name
2: (owner) a username
3: (moddate) a timestamp
4: (user_permission) a permission
5: (globalread) a permission

</pre>

=end html

=begin text

a reference to a list containing 6 items:
0: (id) a ws_id
1: (workspace) a ws_name
2: (owner) a username
3: (moddate) a timestamp
4: (user_permission) a permission
5: (globalread) a permission


=end text

=back



=head2 CreateWorkspaceParams

=over 4



=item Description

Input parameters for the "create_workspace" function.

        Required:
        ws_name workspace - name of the workspace to be created.
        Optional:
        permission globalread - 'r' to set workspace globally readable, default 'n'.
        string description - A free-text description of the workspace, 1000 characters max. Longer strings will be mercilessly and brutally truncated.


=item Definition

=begin html

<pre>
a reference to a hash where the following keys are defined:
workspace has a value which is a ws_name
globalread has a value which is a permission
description has a value which is a string

</pre>

=end html

=begin text

a reference to a hash where the following keys are defined:
workspace has a value which is a ws_name
globalread has a value which is a permission
description has a value which is a string


=end text

=back



=head2 SetPermissionsParams

=over 4



=item Description

Input parameters for the "set_permissions" function.

        One, and only one, of the following is required:
        ws_id id - the numerical ID of the workspace.
        ws_name workspace - name of the workspace or the workspace ID in KBase format, e.g. kb|ws.78.
        Required arguments:
        permission new_permission - the permission to assign to the users.
        list<username> users - the users whose permissions will be altered.


=item Definition

=begin html

<pre>
a reference to a hash where the following keys are defined:
workspace has a value which is a ws_name
id has a value which is a ws_id
new_permission has a value which is a permission
users has a value which is a reference to a list where each element is a username

</pre>

=end html

=begin text

a reference to a hash where the following keys are defined:
workspace has a value which is a ws_name
id has a value which is a ws_id
new_permission has a value which is a permission
users has a value which is a reference to a list where each element is a username


=end text

=back



=cut

package Bio::KBase::workspace::Client::RpcClient;
use base 'JSON::RPC::Client';

#
# Override JSON::RPC::Client::call because it doesn't handle error returns properly.
#

sub call {
    my ($self, $uri, $obj) = @_;
    my $result;

    if ($uri =~ /\?/) {
       $result = $self->_get($uri);
    }
    else {
        Carp::croak "not hashref." unless (ref $obj eq 'HASH');
        $result = $self->_post($uri, $obj);
    }

    my $service = $obj->{method} =~ /^system\./ if ( $obj );

    $self->status_line($result->status_line);

    if ($result->is_success) {

        return unless($result->content); # notification?

        if ($service) {
            return JSON::RPC::ServiceObject->new($result, $self->json);
        }

        return JSON::RPC::ReturnObject->new($result, $self->json);
    }
    elsif ($result->content_type eq 'application/json')
    {
        return JSON::RPC::ReturnObject->new($result, $self->json);
    }
    else {
        return;
    }
}


sub _post {
    my ($self, $uri, $obj) = @_;
    my $json = $self->json;

    $obj->{version} ||= $self->{version} || '1.1';

    if ($obj->{version} eq '1.0') {
        delete $obj->{version};
        if (exists $obj->{id}) {
            $self->id($obj->{id}) if ($obj->{id}); # if undef, it is notification.
        }
        else {
            $obj->{id} = $self->id || ($self->id('JSON::RPC::Client'));
        }
    }
    else {
        # $obj->{id} = $self->id if (defined $self->id);
	# Assign a random number to the id if one hasn't been set
	$obj->{id} = (defined $self->id) ? $self->id : substr(rand(),2);
    }

    my $content = $json->encode($obj);

    $self->ua->post(
        $uri,
        Content_Type   => $self->{content_type},
        Content        => $content,
        Accept         => 'application/json',
	($self->{token} ? (Authorization => $self->{token}) : ()),
    );
}



1;