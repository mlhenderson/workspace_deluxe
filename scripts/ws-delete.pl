#!/usr/bin/env perl
########################################################################
# Authors: Christopher Henry, Scott Devoid, Paul Frybarger
# Contact email: chenry@mcs.anl.gov
# Development location: Mathematics and Computer Science Division, Argonne National Lab
########################################################################
use strict;
use warnings;
use Getopt::Long::Descriptive;
use Text::Table;
use Bio::KBase::workspace::ScriptHelpers qw(get_ws_client workspace parseObjectMeta parseWorkspaceMeta);

my $serv = get_ws_client();
#Defining globals describing behavior
my $primaryArgs = ["Object ID or Name"];
my $translation = {
	"Object ID or Name" => "id",
	workspace => "workspace"
};
#Defining usage and options
my ($opt, $usage) = describe_options(
    'kbws-delete <'.join("> <",@{$primaryArgs}).'> %o',
    [ 'workspace|w:s', 'ID for workspace', {"default" => workspace()} ],
    [ 'restore:s', 'Restore the specified deleted object', {"default" => workspace()} ],
    [ 'showerror|e', 'Show any errors in execution',{"default"=>0}],
    [ 'help|h|?', 'Print this usage information' ]  
);
if (defined($opt->{help})) {
	print $usage;
	exit;
}
#Processing primary arguments
foreach my $arg (@{$primaryArgs}) {
	$opt->{$arg} = shift @ARGV;
	if (!defined($opt->{$arg})) {
		print $usage;
		exit 1;
	}
}
#Instantiating parameters
my $versionString='';
if (defined($opt->{version})) {
	$versionString="/".$opt->{version};
}
my $params = [{
	      ref => $opt->{workspace} ."/".$opt->{"Object ID or Name"} .$versionString,
	      }];

#Calling the server
my $output;
if ($opt->{restore}) {
	if ($opt->{showerror} == 0){
		eval { $serv->delete_objects($params); };
		if($@) {
			print "Cannot restore object!\n";
			print STDERR $@->{message}."\n";
			if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
			print STDERR "\n";
			exit 1;
		}
	} else {
		$serv->delete_objects($params);
	}
	print "Object successfully deleted.\n";
} else {
	
	if ($opt->{showerror} == 0){
		eval { $serv->undelete_objects($params); };
		if($@) {
			print "Cannot delete object!\n";
			print STDERR $@->{message}."\n";
			if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
			print STDERR "\n";
			exit 1;
		}
	} else {
		$serv->undelete_objects($params);
	}
	print "Object successfully restored.\n";
}
exit 0;