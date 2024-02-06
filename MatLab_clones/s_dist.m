function dist=s_dist(z1,z2)
%dist=s_dist(z1,z2) spherical distance.
%   recall that z1 and z2 are in (theta,phi) form

S_TOLER=.0000000000001;

if abs(z2-z1)<S_TOLER
	dist=0.0;
  	return;
end

v1 = s_pt_to_vec(z1);
v2 = s_pt_to_vec(z2);
dotprod = v1(1)*v2(1)+v1(2)*v2(2)+v1(3)*v2(3);
if abs(dotprod) > (1.0 - S_TOLER)
	dist=pi;
else
	dist=acos(dotprod);
end

end
  